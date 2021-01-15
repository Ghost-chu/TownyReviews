package com.bilicraft.townyreviews;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.NationPreRenameEvent;
import com.palmergames.bukkit.towny.event.NewNationEvent;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.event.TownPreRenameEvent;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.requests.GatewayIntent;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class TownyReviews extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;
    private InitListener initListener;
    private DiscordRequestPool discordRequestPool;

    @Override
    public void onLoad() {
        initListener = new InitListener(this);
        DiscordSRV.api.subscribe(initListener);
        try{
            DiscordSRV.api.requireIntent(GatewayIntent.GUILD_MESSAGES);
            DiscordSRV.api.requireIntent(GatewayIntent.GUILD_MESSAGE_REACTIONS);
            DiscordSRV.api.requireIntent(GatewayIntent.GUILD_EMOJIS);
            DiscordSRV.api.requireIntent(GatewayIntent.GUILD_PRESENCES);

            for(GatewayIntent intent : GatewayIntent.values()){
                getLogger().info("Required: "+intent.name());
                DiscordSRV.api.requireIntent(intent);
            }

        }catch (IllegalStateException e){
            getLogger().warning("Failed to intent requirement.");
            e.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        this.reload();
        getConfig().options().copyDefaults(true);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("TownyReviews now enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        this.save();
        DiscordSRV.api.unsubscribe(initListener);
    }

    public void setDiscordRequestPool(DiscordRequestPool discordRequestPool) {
        this.discordRequestPool = discordRequestPool;
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

            sendDiscordWebhook(ReviewType.valueOf(args[1])
                    , StatusType.SUCCESS
                    , sender
                    , "名称 "+args[2] +" 申请已被管理员批准");


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
        if(!review(event.getNation().getName())){
            TownyAPI.getInstance().getDataSource().deleteNation(event.getNation());
            return;
        }
        event.getNation().getKing().getPlayer().sendMessage(ChatColor.YELLOW
                + "城邦创建成功并已加入审核队列，审核期间您的城邦将使用随机名称，审核通过后您的城邦才会显示正常名称。");
        getDiscordRequestPool().createNationPendingRequest(event.getNation().getName(), event.getNation().getKing().getPlayer().getName(),event.getNation());
        try {
            TownyAPI.getInstance().getDataSource().renameNation(event.getNation(),getMaskedName(event.getNation().getUuid()));
        } catch (AlreadyRegisteredException | NotRegisteredException e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNationRename(NationPreRenameEvent event) {
        if(!review(event.getNewName())){
            event.setCancelled(true);
            return;
        }
        event.getNation().getResidents().forEach(resident -> {
            Player player = resident.getPlayer();
            if(player != null){
                player.sendMessage(ChatColor.YELLOW + "你所在的城邦的重命名操作已加入审核队列，审核期间城邦将使用旧名称，审核通过后城邦才会显示新的名称。");
            }
        });
        event.getNation().getKing().getPlayer().sendMessage(ChatColor.YELLOW
                + "重命名操作已加入审核队列，审核期间您的城邦将使用旧名称，审核通过后您的城邦才会显示新的名称。");
        event.setCancelled(true);
        getDiscordRequestPool().createNationPendingRequest(event.getNewName(),event.getNation().getName(), event.getNation());

    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTownCreate(NewTownEvent event) {
        if(!review(event.getTown().getName())){
            TownyAPI.getInstance().getDataSource().deleteTown(event.getTown());
            return;
        }
        event.getTown().getMayor().getPlayer().sendMessage(ChatColor.YELLOW
                + "城镇创建成功并已加入审核队列，审核期间城镇将使用随机名称，审核通过后城镇才会显示正常名称。");
        getDiscordRequestPool().createTownPendingRequest(event.getTown().getName(),event.getTown().getMayor().getName(), event.getTown());
        try {
            TownyAPI.getInstance().getDataSource().renameTown(event.getTown(),getMaskedName(event.getTown().getUuid()));
        } catch (AlreadyRegisteredException | NotRegisteredException e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTownRename(TownPreRenameEvent event) {
        if(!review(event.getNewName())){
            event.setCancelled(true);
            return;
        }
        event.getTown().getResidents().forEach(resident -> {
            Player player = resident.getPlayer();
            if(player != null){
                player.sendMessage(ChatColor.YELLOW + "你所在的城镇的重命名操作已加入审核队列，审核期间城镇将使用旧名称，审核通过后城镇才会显示新的名称。");
            }
        });
        event.setCancelled(true);
        getDiscordRequestPool().createTownPendingRequest(event.getNewName(),event.getTown().getName(),event.getTown());
    }


    public DiscordRequestPool getDiscordRequestPool() {
        return discordRequestPool;
    }

    private boolean review(String name) {
        if (name.contains(" ")) {
            getLogger().info("城镇 [" + name + "] 正试图创建带有空格的名字，这是不允许的行为，已自动拒绝。");
            return false;
        }
        return true;
    }

    public String getMaskedName(UUID uuid){
        return StringUtils.left(uuid.toString().replace("-",""),20);
    }


    public void sendDiscordWebhook(ReviewType reviewType, StatusType statusType, CommandSender sender, String
            msgBody) {
        getLogger().info("[LOG] " + reviewType.getName() + " " + statusType.getName() + " " + sender.getName() + " " + msgBody);
//        WebhookClientBuilder builder = new WebhookClientBuilder(getConfig().getString("webhook")); // or id, token
//        builder.setThreadFactory((job) -> {
//            Thread thread = new Thread(job);
//            thread.setName("TownyReviews - Work Thread");
//            thread.setDaemon(true);
//            return thread;
//        });
//        builder.setWait(true);
//        WebhookClient client = builder.build();
//        String msgTitle = "新的 " + reviewType.getName() + statusType.getName() + " 通知";
//        client.send(new WebhookEmbedBuilder()
//                .setAuthor(new WebhookEmbed.EmbedAuthor("TownyReviews", "https://s3.ax1x.com/2021/01/13/sUuWFO.jpg", "https://www.bilicraft.com"))
//                .setColor(15258703)
//                .setDescription(statusType == StatusType.SUCCESS ? "操作成功通知" : "有新的等待批准的请求")
//                .setTitle(new WebhookEmbed.EmbedTitle(msgTitle, "https://www.bilicraft.com"))
//                .addField(new WebhookEmbed.EmbedField(true, "类别", reviewType.getName()))
//                .addField(new WebhookEmbed.EmbedField(true, "审核", statusType.getName()))
//                .addField(new WebhookEmbed.EmbedField(true, "执行人", sender.getName()))
//                .addField(new WebhookEmbed.EmbedField(true, "时间", new Date().toLocaleString()))
//                .addField(new WebhookEmbed.EmbedField(false, "详细信息", msgBody))
//                .setFooter(new WebhookEmbed.EmbedFooter("本消息由 TownyReviews 发送，操作审核请登录控制台", null))
//                .build()
//        );

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
