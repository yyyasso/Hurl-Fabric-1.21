package net.yyasso.hurl;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hurl implements ModInitializer {
	public static final String MOD_ID = "hurl";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {


		LOGGER.info("Initializing Hurl (Mace Mod)");
	}
}