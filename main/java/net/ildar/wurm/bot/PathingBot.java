package net.ildar.wurm.bot;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.NearTerrainDataBuffer;
import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.TilePicker;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.TargetWindow;
import com.wurmonline.client.renderer.structures.FenceData;
import com.wurmonline.client.renderer.structures.HouseData;
import com.wurmonline.client.renderer.structures.HouseWallData;
import com.wurmonline.client.renderer.structures.StructureData;
import com.wurmonline.math.Vector2f;
import com.wurmonline.shared.constants.PlayerAction;
import com.wurmonline.shared.constants.StructureTypeEnum;

import net.ildar.wurm.Utils;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils.Cell;
import net.ildar.wurm.Utils.Vec2i;
import net.ildar.wurm.annotations.BotInfo;

@BotInfo(name = "Pathing", abbreviation = "pt", description = "Bot that can perform pathfinding to accomplish its various tasks")
public class PathingBot extends Bot
{
	final ForkJoinPool pool = new ForkJoinPool(
		4,
		new ForkJoinPool.ForkJoinWorkerThreadFactory()
		{
			int counter = 0;
			
			public ForkJoinWorkerThread newThread(ForkJoinPool pool)
			{
				ForkJoinWorkerThread thread = new WorkerThread(pool);
				thread.setName(String.format(
					"%s-worker-%s",
					PathingBot.class.getSimpleName(),
					counter++
				));
				return thread;
			}
		},
		(thread, err) -> {
			StringWriter writer = new StringWriter();
			err.printStackTrace(new PrintWriter(writer));
			Utils.consolePrint(
				"%s: uncaught exception in task:\n%s",
				PathingBot.class.getSimpleName(),
				writer.toString()
			);
		},
		true
	);
	
	final HeadsUpDisplay hud = WurmHelper.hud;
    final World world = hud.getWorld();
	
	volatile boolean exiting = false;
	
	static final float tickDelta = 1 / 6f;
	volatile float topSpeedMPS = (15f * 1000) / 60 / 60;
	
	InputHandler inPool(InputHandler fn)
	{
		return args -> pool.execute(() -> fn.handle(args));
	}
	
	boolean waitOnPauseFJ()
	{
		try
		{
			ForkJoinPool.managedBlock(new PauseBlocker(this));
			return !exiting;
		}
		catch(InterruptedException err) { return false; }
	}
	
	public PathingBot()
	{
		registerInputHandler(Inputs.speed, inPool(this::cmdSpeed));
		registerInputHandler(Inputs.walkto, inPool(this::cmdWalkto));
		registerInputHandler(Inputs.follow, inPool(this::cmdFollow));
		registerInputHandler(Inputs.murder, inPool(this::cmdMurder));
		registerInputHandler(Inputs.groom, inPool(args -> runCreatureTask(groomTask)));
		registerInputHandler(Inputs.shear, inPool(args -> runCreatureTask(shearTask)));
	}
	
	void cmdSpeed(String[] args)
	{
		if(args == null || args.length == 0)
		{
			printInputKeyUsageString(Inputs.speed);
			return;
		}
		
		final float newSpeed;
		try
		{
			newSpeed = Float.parseFloat(args[0]);
		}
		catch(NumberFormatException err)
		{
			Utils.consolePrint("`%s` is not a number", args[0]);
			return;
		}
		if(!(newSpeed > 0) || Float.isInfinite(newSpeed))
		{
			Utils.consolePrint("Speed must be a positive number");
			return;
		}
		topSpeedMPS = (newSpeed * 1000) / 60 / 60;
		Utils.consolePrint(
			"Bot will now move at %f kph (%f m/s)",
			newSpeed,
			topSpeedMPS
		);
	}
	
	void enforceNoTasksRunning()
	{
		if(walking || following || murdering || groomTask.running || shearTask.running)
			throw new RuntimeException("Another task is already enabled");
	}
	
	volatile boolean walking = false;
	void cmdWalkto(String[] args)
	{
		final Vec2i target;
		if(args == null || args.length < 2)
		{
			final PickableUnit unit = world.getCurrentHoveredObject();
			if(!(unit instanceof TilePicker))
			{
				Utils.consolePrint("No coords specified and not hovering over any tile");
				printInputKeyUsageString(Inputs.walkto);
				return;
			}
			
			final TilePicker tile = (TilePicker)unit;
			try
			{
				target = new Vec2i(
					Utils.getField(tile, "x"),
					Utils.getField(tile, "y")
				);
			}
			catch(Exception err)
			{
				Utils.consolePrint("Couldn't get coords for hovered tile:\n%s", err);
				return;
			}
		}
		else
		{
			try
			{
				target = new Vec2i(
					Integer.parseInt(args[0]),
					Integer.parseInt(args[1])
				);
			}
			catch(NumberFormatException err)
			{
				Utils.consolePrint("Tile coordinates must be integers");
				printInputKeyUsageString(Inputs.walkto);
				return;
			}
		}
		
		enforceNoTasksRunning();
		walking = true;
		
		Utils.consolePrint("Pathfinding to %d,%d", target.x, target.y);
		if(walkPath(() -> target) != WalkStatus.complete)
			Utils.consolePrint("Couldn't find a path/interrupted");
		walking = false;
	}
	
	volatile boolean following = false;
	void cmdFollow(String[] args)
	{
		if(following)
		{
			following = false;
			return;
		}
		
		if(args == null || args.length < 1)
		{
			printInputKeyUsageString(Inputs.follow);
			return;
		}
		final String targetPlayerName = args[0].toLowerCase();
		
		enforceNoTasksRunning();
		
		following = true;
		while(!exiting && following)
		{
			CreatureCellRenderable _targetPlayer = null;
			for(int i = 0; i < 10; i++)
			{
				_targetPlayer = Utils
					.findCreatures((creature, data) ->
						creature.getModelName().toString().contains(".player") &&
						creature.getHoverName().toLowerCase().startsWith(targetPlayerName)
					)
					.stream()
					.findFirst()
					.orElse(null)
				;
				if(_targetPlayer != null) break;
				Utils.rethrow(() -> ForkJoinPool.managedBlock(new SleepBlocker(250)));
			}
			if(_targetPlayer == null)
			{
				Utils.consolePrint("Couldn't find any players with name `%s`", targetPlayerName);
				break;
			}
			
			final CreatureCellRenderable targetPlayer = _targetPlayer;
			final Supplier<Vec2i> targetPos = () -> new Vec2i(
				(int)(targetPlayer.getXPos() / 4f),
				(int)(targetPlayer.getYPos() / 4f)
			);
			final Vec2i currentPos = targetPos.get();
			if(currentPos.x == world.getPlayerCurrentTileX() && currentPos.y == world.getPlayerCurrentTileY())
			{
				Utils.rethrow(() -> ForkJoinPool.managedBlock(new SleepBlocker(250)));
				continue;
			}
			
			WalkStatus res = walkPath(targetPos);
			if(res == WalkStatus.interrupted)
				continue;
			else if(res == WalkStatus.noPath)
			{
				try
				{
					hud.addOnscreenMessage(
						String.format("Couldn't find path to %s", targetPlayer.getHoverName()),
						1f,
						.5f,
						0f,
						(byte)1
					);
					ForkJoinPool.managedBlock(new SleepBlocker(5000));
					continue;
				}
				catch(Exception err) { Utils.consolePrint("%s", err); }
			}
		}
		following = false;
	}
	
	volatile boolean murdering = false;
	static final Pattern petItemRe = Pattern.compile("\\w's pet$", Pattern.CASE_INSENSITIVE);
	void cmdMurder(String[] args)
	{
		if(murdering)
		{
			murdering = false;
			return;
		}
		
		// TODO: only target hostile/passive
		
		enforceNoTasksRunning();
		
		CreationWindow creationWindow = WurmHelper.hud.getCreationWindow();
		Object progressBar = Utils.rethrow(() -> Utils.getField(creationWindow, "progressBar"));
		
		murdering = true;
		HashSet<Long> ignoredCreatures = new HashSet<>();
		List<CreatureCellRenderable> creatures;
		TargetWindow targetWindow = Utils.rethrow(() -> Utils.getField(hud, "targetWindow"));
		CreatureCellRenderable target = null;
		outer: while(!exiting && murdering)
		{
			long targetId = -1;
			while(murdering && target != null && (targetId = Utils.rethrow(() -> (long)Utils.getField(targetWindow, "targetId"))) > 0)
			{
				if(targetId != target.getId())
				{
					hud.sendAction(PlayerAction.TARGET, target.getId());
					Utils.rethrow(() -> ForkJoinPool.managedBlock(new SleepBlocker(250)));
					if(exiting || !murdering) break outer;
				}
				
				if(Utils.sqdistFromPlayer(target) > 4 * 4)
				{
					final CreatureCellRenderable _target = target; // capture restrictions
					final Supplier<Vec2i> targetPos = () -> new Vec2i(
						(int)(_target.getXPos() / 4f),
						(int)(_target.getYPos() / 4f)
					);
					WalkStatus res = walkPath(targetPos);
					if(res == WalkStatus.noPath)
					{
						ignoredCreatures.add(target.getId());
						target = null;
						hud.sendAction(PlayerAction.NO_TARGET, -1);
						continue outer;
					}
					// TODO: position player a reasonable distance from target
					if(exiting || !murdering) break outer;
				}
				
				Utils.rethrow(() -> ForkJoinPool.managedBlock(new SleepBlocker(1000)));
				if(exiting || !murdering) break outer;
			}
			
			while(
				Utils.getPlayerStamina() < 0.99 ||
				creationWindow.getActionInUse() > 0 ||
				Utils.rethrow(() -> Utils.<Object, Float>getField(progressBar, "progress")) > 0f
			)
			{
				Utils.rethrow(() -> ForkJoinPool.managedBlock(new SleepBlocker(1000)));
				if(exiting || !murdering) break outer;
			}
			
			creatures = Utils.findCreatures((creature, data) ->
				!ignoredCreatures.contains(creature.getId()) &&
				!creature.isItem() &&
				creature.getKingdomId() == 0 &&
				!creature.isControlled() &&
				!creature.getHoverName().startsWith("preserved") &&
				!petItemRe.matcher(data.getHoverText()).find()
			);
			creatures.sort((l, r) -> Float.compare(Utils.sqdistFromPlayer(l), Utils.sqdistFromPlayer(r)));
			
			target = creatures.stream().findFirst().orElse(null);
			if(target == null)
			{
				Utils.consolePrint("Can't find any creatures to target");
				break;
			}
			
			hud.sendAction(PlayerAction.TARGET, target.getId());
			Utils.consolePrint("Murdering `%s`", target.getHoverName());
			Utils.rethrow(() -> ForkJoinPool.managedBlock(new SleepBlocker(250)));
		}
		murdering = false;
	}
	
	// a task that walks to each matching creature in turn and uses a tool on it
	static final class CreatureTask
	{
		final String toolName;
		final String noToolMessage;
		final String verb; // e.g. "groom"
		final String label; // e.g. "Grooming"
		final PlayerAction action;
		final Predicate<CreatureCellRenderable> filter;
		volatile boolean running = false;
		// set by the task, run by the message processors when the action starts/finishes
		volatile Runnable onStart = null;
		volatile Runnable onDone = null;
		
		CreatureTask(String toolName, String noToolMessage, String verb, String label, PlayerAction action, Predicate<CreatureCellRenderable> filter)
		{
			this.toolName = toolName;
			this.noToolMessage = noToolMessage;
			this.verb = verb;
			this.label = label;
			this.action = action;
			this.filter = filter;
		}
		
		void started()
		{
			final Runnable fn = onStart;
			if(fn != null)
				fn.run();
		}
		
		void done()
		{
			final Runnable fn = onDone;
			if(fn != null)
				fn.run();
		}
	}
	
	final CreatureTask groomTask = new CreatureTask(
		"grooming brush",
		"Cannot groom without a brush!",
		"groom",
		"Grooming",
		PlayerAction.GROOM,
		creature -> Utils.isGroomableCreature(creature)
	);
	final CreatureTask shearTask = new CreatureTask(
		"scissors",
		"Cannot shear without scissors!",
		"shear",
		"Shearing",
		PlayerAction.SHEAR,
		creature -> creature.getHoverName().endsWith("sheep") || creature.getHoverName().endsWith("ram")
	);
	
	void runCreatureTask(CreatureTask task)
	{
		if(task.running)
		{
			task.running = false;
			return;
		}

		enforceNoTasksRunning();

		final InventoryMetaItem toolItem = Utils.locateToolItem(task.toolName);
		if(toolItem == null)
		{
			Utils.consolePrint(task.noToolMessage);
			return;
		}

		CreationWindow creationWindow = WurmHelper.hud.getCreationWindow();
		Object progressBar = Utils.rethrow(() -> Utils.getField(creationWindow, "progressBar"));

		task.running = true;
		final HashSet<Long> ignoredCreatures = new HashSet<>();
		List<CreatureCellRenderable> creatures;
		final Cell<CreatureCellRenderable> target = new Cell<>(null);
		// written by the message processors
		final AtomicBoolean started = new AtomicBoolean(false);
		final AtomicBoolean done = new AtomicBoolean(false);
		outer: while(!exiting && task.running)
		{
			if(done.get())
			{
				if(target.val != null)
					ignoredCreatures.add(target.val.getId());
				target.val = null;
				done.set(false);
			}

			if(target.val == null)
			{
				creatures = Utils.findCreatures((creature, data) ->
					!ignoredCreatures.contains(creature.getId()) &&
					!creature.isItem() &&
					creature.getKingdomId() == 0 &&
					!creature.isControlled() &&
					!creature.getHoverName().startsWith("preserved") &&
					task.filter.test(creature) &&
					!petItemRe.matcher(data.getHoverText()).find()
				);
				creatures.sort((l, r) -> Float.compare(Utils.sqdistFromPlayer(l), Utils.sqdistFromPlayer(r)));

				target.val = creatures.stream().findFirst().orElse(null);
				if(target.val == null)
				{
					Utils.consolePrint("Can't find any creatures to %s", task.verb);
					break;
				}
				
				task.onDone = () -> {
					done.set(true);
					task.onDone = null;
				};
			}

			while(Utils.sqdistFromPlayer(target.val) > 4 * 4)
			{
				if(exiting || !task.running)
					break outer;

				final Supplier<Vec2i> targetPos = () -> new Vec2i(
					(int)(target.val.getXPos() / 4f),
					(int)(target.val.getYPos() / 4f)
				);
				WalkStatus res = walkPath(targetPos);
				if(res == WalkStatus.noPath)
				{
					ignoredCreatures.add(target.val.getId());
					task.onDone = null;
					target.val = null;
					hud.sendAction(PlayerAction.NO_TARGET, -1);
					continue outer;
				}
				else if(res == WalkStatus.interrupted)
					continue;
				break;
			}

			final long actionSent = System.currentTimeMillis();
			started.set(false);
			task.onStart = () -> {
				started.set(true);
				task.onStart = null;
			};
			hud.getWorld().getServerConnection().sendAction(
				toolItem.getId(),
				new long[]{target.val.getId()},
				task.action
			);
			Utils.consolePrint("%s `%s`", task.label, target.val.getHoverName());
			
			while(!started.get() && !done.get())
			{
				Utils.rethrow(() -> ForkJoinPool.managedBlock(new SleepBlocker(250)));
				if(exiting || !task.running) break outer;
				
				// action never started; skip this creature rather than retrying it forever
				if(System.currentTimeMillis() - actionSent >= 5000)
				{
					Utils.consolePrint("Timed out waiting for %s to start", task.label.toLowerCase());
					ignoredCreatures.add(target.val.getId());
					task.onDone = null;
					target.val = null;
					continue outer;
				}
			}
			// already done
			if(done.get()) continue outer;

			while(
				Utils.getPlayerStamina() < 0.99 ||
				creationWindow.getActionInUse() > 0 ||
				Utils.rethrow(() -> Utils.<Object, Float>getField(progressBar, "progress")) > 0f
			)
			{
				Utils.rethrow(() -> ForkJoinPool.managedBlock(new SleepBlocker(250)));
				if(exiting || !task.running) break outer;
			}
		}
		task.running = false;
	}

	@Override
	void work() throws Exception
	{
		registerEventProcessor(
			line -> line.contains("You start to tend to"),
			groomTask::started
		);
		registerEventProcessor(
			line ->
				line.contains("You have now tended to") ||
				line.contains("is already well tended") ||
				line.contains("That would be illegal here.")
			,
			groomTask::done
		);

		registerEventProcessor(
			line -> line.contains("You start shearing"),
			shearTask::started
		);
		registerEventProcessor(
			line ->
				line.contains("You finish shearing") ||
				line.contains("is already sheared"),
			shearTask::done
		);

		try
		{
			while(isActive()) {
				// doesn't seem to be a way to donate this thread to the pool
				// so we just sleep I guess
				Thread.sleep(1000);
			}
		}
		catch(InterruptedException err) {}
		finally
		{
			exiting = true;
			// wake tasks blocked on pause so they can see that we're exiting
			synchronized(this) { notifyAll(); }
			pool.shutdown();
			if(!pool.awaitTermination(10, TimeUnit.SECONDS))
				Utils.consolePrint(
					"%s: task pool did not shut down within 10 seconds",
					PathingBot.class.getSimpleName()
				);
		}
	}
	
	static enum WalkStatus
	{
		complete,
		interrupted,
		noPath,
		;
	}
	
	// move in straight line from current tile to given tile, without checking for collisions
	WalkStatus walkLine(int tileX, int tileY)
	{
		// always move from center to center
		Utils.moveToCenter();
		
		Vector2f dest = new Vector2f(tileX * 4 + 2, tileY * 4 + 2);
		Vector2f plyPos = new Vector2f(world.getPlayerPosX(), world.getPlayerPosY());
		Vector2f totalMovement = dest.subtract(plyPos);
		float dist = totalMovement.length();
		
		Vector2f direction = totalMovement.normalize();
		Vector2f step = new Vector2f();
		
		while(!exiting && dist > 0)
		{
			if(!waitOnPauseFJ())
				return WalkStatus.interrupted;
			
			float toMove = Math.min(dist, topSpeedMPS * tickDelta);
			dist -= toMove;
			direction.mult(toMove, step);
			plyPos.addLocal(step);
			Utils.movePlayer(plyPos.x, plyPos.y);
			
			try { ForkJoinPool.managedBlock(new SleepBlocker((long)(1000 * tickDelta))); }
			catch(InterruptedException err) { return WalkStatus.interrupted; }
		}
		return WalkStatus.complete;
	}
	
	// finds and then moves along an unobstructed path from current tile to given tile
	WalkStatus walkPath(Supplier<Vec2i> target)
	{
		try
		{
			final Vec2i pathTarget = target.get();
			final Collection<Vec2i> path = findPath(pathTarget.x, pathTarget.y);
			if(path == null) return WalkStatus.noPath;
			boolean startingTile = true;
			for(Vec2i tileCoords: path)
			{
				if(startingTile)
					startingTile = false;
				else
				{
					final Dir dir = Dir.of(new Vec2i(tileCoords.x - world.getPlayerCurrentTileX(), tileCoords.y - world.getPlayerCurrentTileY()));
					Utils.turnPlayer(dir.yaw(), Float.NaN);
				}
				
				if(walkLine(tileCoords.x, tileCoords.y) == WalkStatus.interrupted)
					return WalkStatus.interrupted;
				
				if(!pathTarget.equals(target.get()))
					return WalkStatus.interrupted;
			}
			return WalkStatus.complete;
		}
		catch(Exception err)
		{
			Utils.consolePrint("Uncaught %s in walkPath: %s", err.getClass().getName(), err.getMessage());
			err.printStackTrace();
			return WalkStatus.interrupted;
		}
	}
	
	ArrayList<Vec2i> findPath(int tileX, int tileY)
	{
		final Vec2i startTile = new Vec2i(world.getPlayerCurrentTileX(), world.getPlayerCurrentTileY());
		final Vec2i endTile = new Vec2i(tileX, tileY);
		CollisionCache cache = new CollisionCache();
		cache.refresh();
		
		// all nodes that have been created; nodes may be removed from path but added again later
		// if a cheaper path through them has been found
		HashMap<Vec2i, PathNode> populated = new HashMap<>();
		
		// set of coords in queue, to elide (linear) search
		HashSet<Vec2i> enqueued = new HashSet<>();
		
		PriorityQueue<PathNode> queue = new PriorityQueue<>(PathNode::compare);
		queue.add(new PathNode(startTile, 0, endTile, null));
		PathNode endNode = null; // node for goal tile
		while(!queue.isEmpty())
		{
			PathNode head = queue.poll();
			if(head.pos.equals(endTile))
			{
				endNode = head;
				break;
			}
			enqueued.remove(head.pos);
			
			for(Dir dir: Dir.values())
			{
				if(dir == Dir.all)
					continue;
				
				final Vec2i neighborTile = new Vec2i(head.pos.x + dir.offset.x, head.pos.y + dir.offset.y);
				if(!cache.isPassable(neighborTile.x, neighborTile.y, Dir.all))
					continue;
				
				final boolean edgePassable;
				if(!dir.diagonal())
					edgePassable = cache.isPassable(head.pos.x, head.pos.y, dir);
				else
				{
					switch(dir)
					{
						// - north and east borders of current tile must be passable
						// - both tiles to north and east must be passable
						// - east border of north tile/north border of east tile must be passable
						// similarly for other cases
						case northeast:
							edgePassable =
								cache.isPassable(head.pos.x, head.pos.y, Dir.north) &&
								cache.isPassable(head.pos.x, head.pos.y, Dir.east) &&
								cache.isPassable(head.pos.x + Dir.east.offset.x, head.pos.y, Dir.all) &&
								cache.isPassable(head.pos.x + Dir.east.offset.x, head.pos.y, Dir.north) &&
								cache.isPassable(head.pos.x, head.pos.y + Dir.north.offset.y, Dir.all) &&
								cache.isPassable(head.pos.x, head.pos.y + Dir.north.offset.y, Dir.east)
							;
							break;
						case northwest:
							edgePassable =
								cache.isPassable(head.pos.x, head.pos.y, Dir.north) &&
								cache.isPassable(head.pos.x, head.pos.y, Dir.west) &&
								cache.isPassable(head.pos.x + Dir.west.offset.x, head.pos.y, Dir.all) &&
								cache.isPassable(head.pos.x + Dir.west.offset.x, head.pos.y, Dir.north) &&
								cache.isPassable(head.pos.x, head.pos.y + Dir.north.offset.y, Dir.all) &&
								cache.isPassable(head.pos.x, head.pos.y + Dir.north.offset.y, Dir.west)
							;
							break;
						case southeast:
							edgePassable =
								cache.isPassable(head.pos.x, head.pos.y, Dir.south) &&
								cache.isPassable(head.pos.x, head.pos.y, Dir.east) &&
								cache.isPassable(head.pos.x + Dir.east.offset.x, head.pos.y, Dir.all) &&
								cache.isPassable(head.pos.x + Dir.east.offset.x, head.pos.y, Dir.south) &&
								cache.isPassable(head.pos.x, head.pos.y + Dir.south.offset.y, Dir.all) &&
								cache.isPassable(head.pos.x, head.pos.y + Dir.south.offset.y, Dir.east)
							;
							break;
						case southwest:
							edgePassable =
								cache.isPassable(head.pos.x, head.pos.y, Dir.south) &&
								cache.isPassable(head.pos.x, head.pos.y, Dir.west) &&
								cache.isPassable(head.pos.x + Dir.west.offset.x, head.pos.y, Dir.all) &&
								cache.isPassable(head.pos.x + Dir.west.offset.x, head.pos.y, Dir.south) &&
								cache.isPassable(head.pos.x, head.pos.y + Dir.south.offset.y, Dir.all) &&
								cache.isPassable(head.pos.x, head.pos.y + Dir.south.offset.y, Dir.west)
							;
							break;
						default:
							throw new IllegalArgumentException(
								String.format("Dir %s is not diagonal", dir)
							);
					}
				}
				
				final int cost = edgePassable ? head.distFromStart + 1 : Integer.MAX_VALUE;
				PathNode neighbor;
				if(!populated.containsKey(neighborTile))
				{
					neighbor = new PathNode(neighborTile, cost, endTile, head);
					populated.put(neighborTile, neighbor);
					if(edgePassable)
					{
						queue.add(neighbor);
						enqueued.add(neighbor.pos);
					}
				}
				else
					neighbor = populated.get(neighborTile);
				
				if(edgePassable && cost < neighbor.distFromStart)
				{
					// the queue doesn't reorder on mutation, so take the node out before lowering its cost
					if(enqueued.contains(neighborTile))
						queue.remove(neighbor);
					neighbor.distFromStart = cost;
					neighbor.previous = head;
					queue.add(neighbor);
					enqueued.add(neighborTile);
				}
			}
		}
		
		if(endNode == null)
		{
			Utils.consolePrint("Couldn't find viable path");
			return null;
		}
		
		// trace path backwards from goal -> player
		ArrayList<Vec2i> path = new ArrayList<>();
		PathNode node = endNode;
		while(node != null)
		{
			path.add(node.pos);
			node = node.previous;
		}
		Collections.reverse(path); // needed path is player -> goal
		return path;
	}
	
	static enum Inputs implements Bot.InputKey
	{
		speed("Speed", "Set speed at which bot will move, in km/h", "real"),
		walkto("Walkto", "Walk to given tile coordinates", "x y"),
		follow("Follow", "Follow the given player", "name"),
		murder("Murder", "Find and murder nearby creatures", ""),
		groom("Grooming", "Find and groom nearby creatures", ""),
		shear("Shear", "Find and shear nearby sheep", ""),
		;

		private final KeyInfo keyInfo;

		Inputs(String fullName, String description, String usage) {
			keyInfo = new KeyInfo(fullName, description, usage);
		}

		@Override
		public KeyInfo keyInfo() {
			return keyInfo;
		}
	}
}

// unnecessarily protected ctor :facepalm:
class WorkerThread extends ForkJoinWorkerThread
{
	public WorkerThread(ForkJoinPool pool)
	{
		super(pool);
	}
}

// that this isn't in the stdlib is typical Java >:|
class SleepBlocker implements ForkJoinPool.ManagedBlocker
{
	long wakeTimeMS;
	
	public SleepBlocker(long durationMS)
	{
		wakeTimeMS = System.currentTimeMillis() + durationMS;
	}
	
	long remaining()
	{
		return wakeTimeMS - System.currentTimeMillis();
	}
	
	@Override
	public boolean block() throws InterruptedException
	{
		Thread.sleep(Math.max(0, remaining()));
		return remaining() <= 0;
	}

	@Override
	public boolean isReleasable()
	{
		return remaining() <= 0;
	}
}

class PauseBlocker implements ForkJoinPool.ManagedBlocker
{
	PathingBot bot;
	public PauseBlocker(PathingBot bot)
	{
		this.bot = bot;
	}
	
	@Override
	public boolean block() throws InterruptedException
	{
		synchronized(bot)
		{
			while(bot.getPaused() && !bot.exiting)
				bot.wait();
		}
		return true;
	}

	@Override
	public boolean isReleasable()
	{
		return !bot.getPaused() || bot.exiting;
	}
}

enum Dir
{
	all(new Vec2i(0, 0)),
	
	northeast(new Vec2i(1, -1)),
	northwest(new Vec2i(-1, -1)),
	southeast(new Vec2i(1, 1)),
	southwest(new Vec2i(-1, 1)),
	
	north(new Vec2i(0, -1)),
	east(new Vec2i(1, 0)),
	south(new Vec2i(0, 1)),
	west(new Vec2i(-1, 0)),
	;
	
	final Vec2i offset;
	Dir(Vec2i offset)
	{
		this.offset = offset;
	}
	
	public static Dir of(Vec2i vec)
	{
		for(Dir val: values())
			if(val.offset.equals(vec))
				return val;
		return Dir.all;
	}
	
	boolean diagonal()
	{
		return offset.x != 0 && offset.y != 0;
	}
	
	public float yaw()
	{
		switch(this)
		{
			case northeast: return 45;
			case northwest: return 315;
			case southeast: return 135;
			case southwest: return 225;
			case north: return 0;
			case east: return 90;
			case south: return 180;
			case west: return 270;
			case all:
		}
		return 22.5f;
	}
}

class PathNode
{
	public Vec2i pos;
	public int distFromStart;
	public double distToEnd;
	public PathNode previous = null;
	
	public PathNode(Vec2i pos, int distFromStart, Vec2i goal, PathNode previous)
	{
		this.pos = pos;
		this.distFromStart = distFromStart;
		// diagonal steps cost the same as straight ones, so Chebyshev distance is the admissible estimate
		this.distToEnd = Math.max(Math.abs(pos.x - goal.x), Math.abs(pos.y - goal.y));
		this.previous = previous;
	}

	public double cost()
	{
		return distFromStart + distToEnd;
	}
	
	public static int compare(PathNode lhs, PathNode rhs)
	{
		final int res = Double.compare(lhs.cost(), rhs.cost());
		// on ties prefer nodes closer to the goal, which avoids exploring many equally good paths
		return res != 0 ? res : Double.compare(lhs.distToEnd, rhs.distToEnd);
	}
}

class CollisionCache
{
	static final int pvsDiameter = 512;
	static final int pvsRadius = pvsDiameter >> 1;
	static final byte flagNorth = 1 << 0;
	static final byte flagWest = 1 << 1;
	static final byte flagTile = 1 << 2;
	
	World world;
	ServerConnectionListenerClass serverConnection;
	Map<Long, StructureData> structures;
	NearTerrainDataBuffer groundBuffer;
	
	byte[] cache;
	Vec2i origin;
	Vec2i mins;
	Vec2i maxs;
	
	public CollisionCache()
	{
		world = WurmHelper.hud.getWorld();
		serverConnection = world.getServerConnection().getServerConnectionListener();
		structures = Utils.rethrow(() -> Utils.getField(serverConnection, "structures"));
		groundBuffer = world.getNearTerrainBuffer();
	}
	
	public void refresh()
	{
		origin = new Vec2i(world.getPlayerCurrentTileX(), world.getPlayerCurrentTileY());
		mins = new Vec2i(origin.x - pvsRadius, origin.y - pvsRadius);
		maxs = new Vec2i(origin.x + pvsRadius, origin.y + pvsRadius);
		
		cache = new byte[pvsDiameter * pvsDiameter];
		
		for(int y = mins.y; y < maxs.y; y++)
			for(int x = mins.x; x < maxs.x; x++)
				if(!isTilePassable(x, y))
					insert(x, y, -1);
		
		for(StructureData v: structures.values())
		{
			if(v instanceof FenceData)
				insert((FenceData)v);
			else if(v instanceof HouseData)
				insert((HouseData)v);
		}
	}
	
	public boolean isPassable(int tileX, int tileY, Dir direction)
	{
		final Vec2i pos;
		try { pos  = posInPVS(tileX, tileY); }
		catch(IllegalArgumentException e) { return false; }
		
		final int index = pos.y * pvsDiameter + pos.x;
		final byte flags = cache[index];
		switch(direction)
		{
			case all:
				return (flags & flagTile) == 0;
			case north:
				return (flags & flagNorth) == 0;
			case west:
				return (flags & flagWest) == 0;
			
			// collision data is only recorded for north/west, east/south must check
			// opposite borders of adjacent tile
			case east:
				return isPassable(tileX + Dir.east.offset.x, tileY, Dir.west);
			case south:
				return isPassable(tileX, tileY + Dir.south.offset.y, Dir.north);
			
			default:
				throw new RuntimeException("unknown direction");
		}
	}
	
	void insert(FenceData fence)
	{
		// for now pathing is restricted to layer 0
		if(fence.getRealHeight() != 0)
			return;
		
		if(isPassableStructure(fence.getType().type))
			return;
		
		insert(fence.getTileX(), fence.getTileY(), fence.getDir());
	}
	
	void insert(HouseData house)
	{
		Map<Long, HouseWallData> walls = Utils.rethrow(() -> Utils.getField(house, "walls"));
		for(HouseWallData wall: walls.values())
			insert(wall);
	}
	
	void insert(HouseWallData wall)
	{
		// for now pathing is restricted to layer 0
		if(wall.getRealHeight() != 0)
			return;
		
		if(isPassableStructure(wall.getType().type))
			return;
		
		insert(wall.getTileX(), wall.getTileY(), wall.getDir());
	}
	
	void insert(int tileX, int tileY, int wurmDirection)
	{
		if(wurmDirection < 0)
		{
			setFlags(tileX, tileY, flagTile);
			return;
		}
		
		final boolean north = wurmDirection == 0;
		setFlags(tileX, tileY, north ? flagNorth : flagWest);
	}
	
	void setFlags(int tileX, int tileY, int newFlags)
	{
		final Vec2i pos = posInPVS(tileX, tileY);
		final int index = pos.y * pvsDiameter + pos.x;
		byte flags = cache[index];
		flags |= newFlags;
		cache[index] = flags;
	}
	
	Vec2i posInPVS(int tileX, int tileY)
	{
		Vec2i pos = new Vec2i((tileX - origin.x) + pvsRadius, (tileY - origin.y) + pvsRadius);
		if(pos.x < 0 || pos.x >= pvsDiameter || pos.y < 0 || pos.y >= pvsDiameter)
			throw new IllegalArgumentException(String.format(
				"Trying to get position %s (local %s) that is out of bounds for PVS at %s (from %s to %s)",
				new Vec2i(tileX, tileY),
				pos,
				origin,
				mins,
				maxs
			));
		return pos;
	}
	
	boolean isPassableStructure(StructureTypeEnum type)
	{
		switch(type)
		{
			default:
				return false;
			case ARCHED:
			case ARCHED_LEFT:
			case ARCHED_RIGHT:
			case ARCHED_T:
			case CURB:
			case FENCE_PLAN_CRUDE:
			case FENCE_PLAN_CRUDE_GATE:
			case FENCE_PLAN_CURB:
			case FENCE_PLAN_GARDESGARD_GATE:
			case FENCE_PLAN_GARDESGARD_HIGH:
			case FENCE_PLAN_GARDESGARD_LOW:
			case FENCE_PLAN_IRON_BARS:
			case FENCE_PLAN_IRON_BARS_GATE:
			case FENCE_PLAN_IRON_BARS_PARAPET:
			case FENCE_PLAN_IRON_BARS_TALL:
			case FENCE_PLAN_IRON_BARS_TALL_GATE:
			case FENCE_PLAN_MEDIUM_CHAIN:
			case FENCE_PLAN_PALISADE:
			case FENCE_PLAN_PALISADE_GATE:
			case FENCE_PLAN_PORTCULLIS:
			case FENCE_PLAN_ROPE_HIGH:
			case FENCE_PLAN_ROPE_LOW:
			case FENCE_PLAN_STONE_FENCE:
			case FENCE_PLAN_STONE_PARAPET:
			case FENCE_PLAN_STONEWALL:
			case FENCE_PLAN_STONEWALL_HIGH:
			case FENCE_PLAN_WOODEN:
			case FENCE_PLAN_WOODEN_GATE:
			case FENCE_PLAN_WOODEN_PARAPET:
			case FENCE_PLAN_WOVEN:
			case FLOWERBED:
			case HEDGE_LOW:
			case HOUSE_PLAN_ARCH_LEFT:
			case HOUSE_PLAN_ARCH_RIGHT:
			case HOUSE_PLAN_ARCH_T:
			case HOUSE_PLAN_ARCHED:
			case HOUSE_PLAN_BALCONY:
			case HOUSE_PLAN_BARRED:
			case HOUSE_PLAN_CANOPY:
			case HOUSE_PLAN_DOOR:
			case HOUSE_PLAN_DOUBLE_DOOR:
			case HOUSE_PLAN_JETTY:
			case HOUSE_PLAN_NARROW_WINDOW:
			case HOUSE_PLAN_ORIEL:
			case HOUSE_PLAN_PORTCULLIS:
			case HOUSE_PLAN_SOLID:
			case HOUSE_PLAN_WINDOW:
			case NO_WALL:
			case PLAN:
				return true;
		}
	}
	
	boolean isTilePassable(int tileX, int tileY)
	{
		switch(groundBuffer.getTileType(tileX, tileY))
		{
			case TILE_BUSH_THORN:
			case TILE_LAVA:
			case TILE_HOLE: // cave entrance without door
			case TILE_MINE_DOOR_GOLD:
			case TILE_MINE_DOOR_SILVER:
			case TILE_MINE_DOOR_STEEL:
			case TILE_MINE_DOOR_STONE:
			case TILE_MINE_DOOR_WOOD:
				return false;
			default:
		}
		
		final float nw = groundBuffer.getHeight(tileX, tileY);
		final float ne = groundBuffer.getHeight(tileX + 1, tileY);
		final float sw = groundBuffer.getHeight(tileX, tileY + 1);
		final float se = groundBuffer.getHeight(tileX + 1, tileY + 1);
		
		final float maxSlope = 30 / 10f;
		final float minHeight = -15 / 10f;
		if(absdiff(nw, ne) > maxSlope || absdiff(nw, sw) > maxSlope)
			return false;
		if(absdiff(se, ne) > maxSlope || absdiff(se, sw) > maxSlope)
			return false;
		if(absdiff(nw, se) > maxSlope || absdiff(ne, sw) > maxSlope)
			return false;
		if(Math.max(Math.max(nw, ne), Math.max(sw, se)) < minHeight)
			return false;
		
		return true;
	}
	
	static float absdiff(float lhs, float rhs)
	{
		return Math.abs(lhs - rhs);
	}
}
