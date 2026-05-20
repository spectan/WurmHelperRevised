package net.ildar.wurm.bot;

import org.junit.Test;

import java.rmi.RemoteException;

import static org.junit.Assert.assertEquals;

public class RMIBotTest {
    @Test
    public void parsesRegistryAddressIntoHostAndPort() {
        RMIBot.RegistryAddress address = RMIBot.parseRegistryAddress("127.0.0.1:11015");

        assertEquals("127.0.0.1", address.host);
        assertEquals(11015, address.port);
    }

    @Test
    public void clientSetReturnsCombatInfoFromRemote() throws Exception {
        ClientSet clients = new ClientSet();
        CombatInfo remoteInfo = new CombatInfo();
        remoteInfo.target = 123L;
        remoteInfo.playerHealth = 0.75f;
        clients.remotes.add(new FakeClient(remoteInfo));

        CombatInfo info = clients.getCombatInfo();

        assertEquals(123L, info.target);
        assertEquals(0.75f, info.playerHealth, 0.001f);
    }

    private static class FakeClient implements BotClient {
        private final CombatInfo combatInfo;

        FakeClient(CombatInfo combatInfo) {
            this.combatInfo = combatInfo;
        }

        @Override
        public String getPlayerName() throws RemoteException {
            return "fake";
        }

        @Override
        public CombatInfo getCombatInfo() throws RemoteException {
            return combatInfo;
        }

        @Override
        public void execCmds(String[] consoleCommands) throws RemoteException {
        }

        @Override
        public void genericAction(short actionID, long targetID) throws RemoteException {
        }

        @Override
        public void setPosAndHeading(float x, float y, float rx, float ry) throws RemoteException {
        }

        @Override
        public void embark(long vehicleID) throws RemoteException {
        }

        @Override
        public void disembark() throws RemoteException {
        }

        @Override
        public void attack(long creatureID) throws RemoteException {
        }

        @Override
        public void dig() throws RemoteException {
        }

        @Override
        public void level(long tileID) throws RemoteException {
        }

        @Override
        public void mine(long wallID, MinerBot.Direction direction) throws RemoteException {
        }

        @Override
        public void clearCrafting() throws RemoteException {
        }
    }
}
