package com.bilicraft.townyreviews;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.db.TownyDataSource;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.entities.User;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class DiscordRequestPool {
    private final Plugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final TextChannel channel;

    public DiscordRequestPool(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cache.yml");
        if (!this.file.exists()) {
            try {
                this.file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        channel = DiscordSRV.getPlugin().getJda().getTextChannelById(plugin.getConfig().getLong("channelid"));
        if (channel == null) {
            return;
        }
        plugin.getLogger().info("Channel selected: " + channel.getName());
    }

    public void createTownPendingRequest(String name, String requester, Town town) {
        channel.sendMessage(new EmbedBuilder()
                .setAuthor("TownyReviews", "https://www.bilicraft.com", "https://s3.ax1x.com/2021/01/13/sUuWFO.jpg")
                .setColor(15258703)
                .setTitle("有待处理的城镇名称审核请求")
                .setDescription("有待处理的城镇名称审核请求，请点击下方的按钮进行审核。")
                .addField("类型", "城镇", true)
                .addField("操作者", requester, true)
                .addField("城镇名称", name, false)
                .build()).queue(message -> {
            message.addReaction("👍").queue(success-> message.addReaction("👎").queue());

            data.set(message.getIdLong() + ".name", name);
            data.set(message.getIdLong() + ".type", "town");
            data.set(message.getIdLong() + ".town", town.getUuid().toString());
            try {
                data.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void createNationPendingRequest(String name, String requester, Nation nation) {
        channel.sendMessage(new EmbedBuilder()
                .setAuthor("TownyReviews", "https://www.bilicraft.com", "https://s3.ax1x.com/2021/01/13/sUuWFO.jpg")
                .setColor(15258703)
                .setTitle("有待处理的城邦名称审核请求")
                .setDescription("有待处理的城邦名称审核请求，请点击下方的按钮进行审核。")
                .addField("类型", "城邦", true)
                .addField("玩家/城镇/城邦名称", requester, true)
                .addField("城邦名称", name, false)
                .build()).queue(message -> {
            message.addReaction("👍").queue(success-> message.addReaction("👎").queue());

            data.set(message.getIdLong() + ".name", name);
            data.set(message.getIdLong() + ".type", "nation");
            data.set(message.getIdLong() + ".nation", nation.getUuid().toString());
            try {
                data.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void renameTown(String townUuid, String newName) {
        TownyDataSource dataSource = TownyAPI.getInstance().getDataSource();
        Town town;
        try {
            dataSource.getTown(newName);
            plugin.getLogger().info("Town name " + newName + " already in used, rename request of " + townUuid + " ignored.");
            //Failed
        } catch (NotRegisteredException e) {
            try {
                town = dataSource.getTown(UUID.fromString(townUuid));
                dataSource.renameTown(town, newName);
            } catch (AlreadyRegisteredException | NotRegisteredException alreadyRegisteredException) {
                plugin.getLogger().info("Failed name " + newName + ", town rename request of " + townUuid + " ignored.");
            }
        }
    }

    private void renameNation(String townUuid, String newName) {
        TownyDataSource dataSource = TownyAPI.getInstance().getDataSource();
        Nation nation;
        try {
            dataSource.getNation(newName);
            plugin.getLogger().info("Nation name " + newName + " already in used, rename request of " + townUuid + " ignored.");
            //Failed
        } catch (NotRegisteredException e) {
            try {
                nation = dataSource.getNation(UUID.fromString(townUuid));
                dataSource.renameNation(nation, newName);
            } catch (AlreadyRegisteredException | NotRegisteredException alreadyRegisteredException) {
                plugin.getLogger().info("Failed name " + newName + ", nation rename request of " + townUuid + " ignored.");
            }
        }
    }

    public void callback(TextChannel textChannel, long messageId, boolean accepted, User processor) {
        ConfigurationSection section = data.getConfigurationSection(String.valueOf(messageId));
        if (section == null) {
            return;
        }
        String type = section.getString("type");
        if (type == null) {
            plugin.getLogger().info("[LOG] 消息 "+messageId+" 指向了一个已损坏的数据.");
            data.set(String.valueOf(messageId), null);
            textChannel.deleteMessageById(messageId).queue();
            return;
        }
        String name = section.getString("name");

        if (!accepted) {
            plugin.getLogger().info("[LOG] 已拒绝 "+name+" 名称。操作人："+processor.getName());
            data.set(String.valueOf(messageId), null);
            textChannel.deleteMessageById(messageId).queue();
            return;
        }
        String uuid;
        if (type.equals("town")) {
            uuid = section.getString("town");
            renameTown(uuid, name);
        } else if (type.equals("nation")) {
            uuid = section.getString("nation");
            renameNation(uuid, name);
        }
        plugin.getLogger().info("[LOG] 已批准 "+name+" 名称。操作人："+processor.getName());
        data.set(String.valueOf(messageId), null);
        try {
            data.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        textChannel.deleteMessageById(messageId).queue();
    }
}
