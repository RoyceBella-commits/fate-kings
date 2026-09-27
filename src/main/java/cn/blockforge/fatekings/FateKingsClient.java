package cn.blockforge.fatekings;

import cn.blockforge.fatekings.client.FateClient;
import net.fabricmc.api.ClientModInitializer;

public final class FateKingsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FateClient.init();
    }
}
