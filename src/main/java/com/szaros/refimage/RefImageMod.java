package com.szaros.refimage;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. Everything this mod does is client-side (rendering + client
 * commands), so there is no common/server setup here — see the classes in
 * the .client package, which are guarded with @EventBusSubscriber(value =
 * Dist.CLIENT) so they never load on a dedicated server.
 */
@Mod(RefImageMod.MODID)
public class RefImageMod {
    public static final String MODID = "refimage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
}
