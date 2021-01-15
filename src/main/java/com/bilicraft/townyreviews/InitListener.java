package com.bilicraft.townyreviews;

import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordReadyEvent;
import github.scarsz.discordsrv.util.DiscordUtil;

public class InitListener {
    private final TownyReviews plugin;

    public InitListener(TownyReviews plugin) {
        this.plugin = plugin;
    }
    @Subscribe
    public void discordReadyEvent(DiscordReadyEvent event) {
        DiscordUtil.getJda().addEventListener(new CallbackListener(plugin));
       plugin.setDiscordRequestPool(new DiscordRequestPool(plugin));
        plugin.getLogger().info("Callback registered :)");
    }
}
