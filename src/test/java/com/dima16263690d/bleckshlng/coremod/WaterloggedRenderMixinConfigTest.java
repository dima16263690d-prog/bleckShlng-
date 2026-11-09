package com.dima16263690d.bleckshlng.coremod;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Verifies that the client chunk-render Mixin is included in the packaged config. */
public class WaterloggedRenderMixinConfigTest {
    @Test
    public void registersRenderChunkMixinOnClient() throws Exception {
        InputStream input = getClass().getResourceAsStream("/mixins.bleckshlng.json");
        assertNotNull("Missing MixinBooter configuration", input);

        byte[] bytes;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            bytes = output.toByteArray();
        } finally {
            input.close();
        }

        String json = new String(bytes, StandardCharsets.UTF_8);
        assertTrue("RenderChunk mixin is not registered",
                json.contains("client.RenderChunkWaterloggedMixin"));
        assertTrue("Mixin config must be client-only",
                json.contains("\"client\""));
    }
}
