package com.bilicraft.townyreviews;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;


public class CallbackListener extends ListenerAdapter {
    private final TownyReviews plugin;

    public CallbackListener(TownyReviews plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onGuildMessageReactionAdd(@NotNull GuildMessageReactionAddEvent event) {
        super.onGuildMessageReactionAdd(event);
        if(event.getUser().equals(DiscordSRV.getPlugin().getJda().getSelfUser())){
            return;
        }
        boolean approve = event.getReaction().getReactionEmote().getAsReactionCode().equals("👍");
        plugin.getLogger().info("[LOG] Reviews action: "+event.getMessageIdLong()+", approval: "+approve);
        plugin.getDiscordRequestPool().callback(event.getChannel(),event.getMessageIdLong(),approve,event.getUser());
    }


}
