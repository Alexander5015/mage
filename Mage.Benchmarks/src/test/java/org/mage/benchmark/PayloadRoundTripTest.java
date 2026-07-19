package org.mage.benchmark;

import mage.remote.traffic.ZippedObject;
import mage.utils.CompressUtil;
import mage.view.GameView;
import org.junit.Test;
import org.mage.benchmark.fixture.DeterministicGameFixture;
import org.mage.benchmark.support.JavaSerialization;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PayloadRoundTripTest {

    @Test
    public void gameViewCompressionPreservesShape() throws Exception {
        GameView original = DeterministicGameFixture.create().getGameView();
        Object zipped = CompressUtil.compress(original);
        assertTrue(zipped instanceof ZippedObject);
        GameView restored = (GameView) CompressUtil.decompress(zipped);
        assertEquals(original.getPlayers().size(), restored.getPlayers().size());
    }

    @Test
    public void gameViewSerializationPreservesShape() throws Exception {
        GameView original = DeterministicGameFixture.create().getGameView();
        byte[] bytes = JavaSerialization.serialize(original);
        GameView restored = (GameView) JavaSerialization.deserialize(bytes);
        assertEquals(original.getPlayers().size(), restored.getPlayers().size());
    }

    @Test
    public void controlPayloadRoundTrips() throws Exception {
        List<String> original = Arrays.asList("game-update", "turn-1", "player-a");
        assertEquals(original, CompressUtil.decompress(CompressUtil.compress(original)));
        assertEquals(original, JavaSerialization.deserialize(JavaSerialization.serialize(original)));
    }
}
