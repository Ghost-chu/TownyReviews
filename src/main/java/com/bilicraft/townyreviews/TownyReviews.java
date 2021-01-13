package com.bilicraft.townyreviews;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.NationPreRenameEvent;
import com.palmergames.bukkit.towny.event.NewNationEvent;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.event.TownPreRenameEvent;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class TownyReviews extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        // Plugin startup logic
        this.reload();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("TownyReviews now enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        this.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("townyreviews.admin")) {
            sender.sendMessage("Permission denied");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Wrong usage! Example: /" + label + " accept <TYPE> <NAME>");
            return true;
        }
        if (args[0].equalsIgnoreCase("accept")) {
            if (!args[1].equals(ReviewType.NATION.name()) && !args[1].equals(ReviewType.TOWN.name())) {
                sender.sendMessage("Wrong type: NATION or TOWN");
                return true;
            }

            List<String> accepted = this.data.getStringList(args[1]);
            if (!accepted.contains(args[2])) {
                accepted.add(args[2]);
            }
            this.data.set(args[1], accepted);
            sender.sendMessage("Accepted: " + args[2]);
            save();
            return true;
        } else {
            sender.sendMessage("Wrong action, can use: accept");
            return true;
        }
    }


    private void reload() {
        saveDefaultConfig();
        this.dataFile = new File(getDataFolder(), "data.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        try {
            this.data.save(this.dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNationCreate(NewNationEvent event) {
        if (review(ReviewType.NATION, event.getNation().getName())) {
            sendDiscordWebhook(ReviewType.NATION
                    , StatusType.SUCCESS
                    , event.getNation().getKing().getPlayer()
                    , "新的国家 " + event.getNation().getName() + " 创建成功");
            return;
        }
        //TownyAPI.getInstance().getDataSource().removeNation(event.getNation());
        event.getNation().getKing().getPlayer().sendMessage(ChatColor.YELLOW
                + "由于您申请创建城邦的城邦名称还未审核通过，因此你的城邦已被修改为随机名称，请等待管理员批准使用名称 " + ChatColor.GREEN + event.getNation().getName()
                + ChatColor.YELLOW + " 后再进行改名。");
        sendDiscordWebhook(ReviewType.NATION
                , StatusType.CREATE_REQUEST
                , event.getNation().getKing().getPlayer()
                , "申请创建新的国家 " + event.getNation().getName() + "，批准请输入命令 `/townyreviews accept " + ReviewType.NATION.name() + " " + event.getNation().getName() + "`");
        try {
            TownyAPI.getInstance().getDataSource().renameNation(event.getNation(),getMaskedName(event.getNation().getUuid()));
        } catch (AlreadyRegisteredException | NotRegisteredException e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNationRename(NationPreRenameEvent event) {
        if (!review(ReviewType.NATION, event.getNewName())) {
            sendDiscordWebhook(ReviewType.NATION
                    , StatusType.SUCCESS
                    , event.getNation().getKing().getPlayer()
                    , "国家改名 从 " + event.getOldName() +" 到 " +event.getNewName()+ " 成功");
            return;
        }
        event.getNation().getKing().getPlayer().sendMessage(ChatColor.YELLOW
                + "由于您申请创建城邦的城邦名称还未审核通过，因此城邦改名被取消，请等待管理员批准使用名称 " + ChatColor.GREEN + event.getNewName()
                + ChatColor.YELLOW + " 后再重新尝试改名。");
        event.setCancelled(true);
        sendDiscordWebhook(ReviewType.NATION
                , StatusType.RENAME_REQUEST
                , event.getNation().getKing().getPlayer()
                , "申请修改名称为 " + event.getNewName() + "，批准请输入命令 `/townyreviews accept " + ReviewType.NATION.name() + " " + event.getNewName() + "`");
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTownCreate(NewTownEvent event) {
        if (review(ReviewType.TOWN, event.getTown().getName())) {
            sendDiscordWebhook(ReviewType.NATION
                    , StatusType.SUCCESS
                    , event.getTown().getMayor().getPlayer()
                    , "新的国家 " + event.getTown().getName() + " 创建成功");
            return;
        }
        //TownyAPI.getInstance().getDataSource().removeTown(event.getTown());

        event.getTown().getMayor().getPlayer().sendMessage(ChatColor.YELLOW
                + "由于您申请创建城镇的城镇名称还未审核通过，因此你的城镇已被修改为随机名称，请等待管理员批准使用名称 " + ChatColor.GREEN + event.getTown().getName()
                + ChatColor.YELLOW + " 后再重新改名。");
        sendDiscordWebhook(ReviewType.TOWN
                , StatusType.CREATE_REQUEST
                , event.getTown().getMayor().getPlayer()
                , "申请创建新的城镇 " + event.getTown().getName() + "，批准请输入命令 `/townyreviews accept " + ReviewType.TOWN.name() + " " + event.getTown().getName() + "`");
        try {
            TownyAPI.getInstance().getDataSource().renameTown(event.getTown(),getMaskedName(event.getTown().getUuid()));
        } catch (AlreadyRegisteredException | NotRegisteredException e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTownRename(TownPreRenameEvent event) {
        if (review(ReviewType.TOWN, event.getNewName())) {
            sendDiscordWebhook(ReviewType.TOWN
                    , StatusType.SUCCESS
                    , event.getTown().getMayor().getPlayer()
                    , "城镇改名 从 " + event.getOldName() +" 到 " +event.getNewName()+ " 成功");
            return;
        }
        event.getTown().getMayor().getPlayer().sendMessage(ChatColor.YELLOW
                + "由于您申请创建城邦的城镇名称还未审核通过，因此城镇改名被取消，请等待管理员批准使用名称 " + ChatColor.GREEN + event.getNewName()
                + ChatColor.YELLOW + " 后再重新尝试改名。");
        event.setCancelled(true);
        sendDiscordWebhook(ReviewType.TOWN
                , StatusType.RENAME_REQUEST
                , event.getTown().getMayor().getPlayer()
                , "申请修改名称为 " + event.getNewName() + "，批准请输入命令 `/townyreviews accept " + ReviewType.TOWN.name() + " " + event.getNewName() + "`");

    }

    private boolean review(ReviewType type, String name) {
        if (!getConfig().getBoolean("reviews." + type.name(), true)) {
            return true;
        }
        if (name.contains(" ")) {
            getLogger().info("城镇 [" + name + "] 正试图创建带有空格的名字，这是不允许的行为，已自动拒绝。");
            return false;
        }
        name = name.trim();
        List<String> acceptedList = data.getStringList(type.name());
        return acceptedList.contains(name);
    }

    public String getMaskedName(UUID uuid){
        return StringUtils.left(uuid.toString().replace("-",""),20);
    }


    public void sendDiscordWebhook(ReviewType reviewType, StatusType statusType, CommandSender sender, String
            msgBody) {
        getLogger().info("[LOG] " + reviewType.getName() + " " + statusType.getName() + " " + sender.getName() + " " + msgBody);
        WebhookClientBuilder builder = new WebhookClientBuilder(getConfig().getString("webhook")); // or id, token
        builder.setThreadFactory((job) -> {
            Thread thread = new Thread(job);
            thread.setName("TownyReviews - Work Thread");
            thread.setDaemon(true);
            return thread;
        });
        builder.setWait(true);
        WebhookClient client = builder.build();
        String msgTitle = "新的 " + reviewType.getName() + statusType.getName() + " 通知";
        client.send(new WebhookEmbedBuilder()
                .setAuthor(new WebhookEmbed.EmbedAuthor("TownyReviews", "https://s3.ax1x.com/2021/01/13/sUuWFO.jpg", "https://www.bilicraft.com"))
                .setColor(15258703)
                .setDescription(statusType == StatusType.SUCCESS ? "操作成功通知" : "有新的等待批准的请求")
                .setTitle(new WebhookEmbed.EmbedTitle(msgTitle, "https://www.bilicraft.com"))
                .addField(new WebhookEmbed.EmbedField(true, "类别", reviewType.getName()))
                .addField(new WebhookEmbed.EmbedField(true, "审核", statusType.getName()))
                .addField(new WebhookEmbed.EmbedField(true, "执行人", sender.getName()))
                .addField(new WebhookEmbed.EmbedField(true, "时间", new Date().toLocaleString()))
                .addField(new WebhookEmbed.EmbedField(false, "详细信息", msgBody))
                .setFooter(new WebhookEmbed.EmbedFooter("本消息由 TownyReviews 发送，操作审核请登录控制台", null))
                .build()
        );
    }


    public enum ReviewType {
        NATION("国家"),
        TOWN("城镇");

        private final String name;

        ReviewType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public enum StatusType {
        SUCCESS("操作成功"),
        RENAME_REQUEST("改名请求"),
        CREATE_REQUEST("创建请求");

        private final String name;

        StatusType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }


}
