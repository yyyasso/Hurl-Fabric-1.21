package net.yyasso.hurl;

import net.fabricmc.api.ModInitializer;

import net.yyasso.hurl.registry.HurlDamageTypes;
import net.yyasso.hurl.registry.HurlEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hurl implements ModInitializer {
	public static final String MOD_ID = "hurl";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		HurlEntityType.initialize();
		HurlDamageTypes.initialize();

		LOGGER.info("Initializing Hurl (Mace Mod)");
	}
}