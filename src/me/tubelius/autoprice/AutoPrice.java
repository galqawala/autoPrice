package me.tubelius.autoprice;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

//import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

//Class (AutoPrice Bukkit plugin)
public class AutoPrice extends JavaPlugin implements Listener {     
    //Public variables (most are loaded from configuration file) 
    public final    Logger      logger          = Logger.getLogger("Minecraft");    //For sending messages to console
    public static   Economy     economy         = null;                             //Vault plugin hook     (to access economy)
    public static   Permission  permission      = null;                             //Vault permission hook (to access permissions)
    //Class instances
    GetData         GetData         = new GetData(this);
    Prices          Prices          = new Prices(this);
    Trade           Trade           = new Trade(this);
    Configuration   Configuration   = new Configuration(this);
    //Set colors to their default values (final colors will be loaded from the configuration file)
    public ChatColor chatColorNormal    = ChatColor.DARK_AQUA;
    public ChatColor chatColorError     = ChatColor.RED;
    
    @Override                               //Replace Bukkit's own event
    public void onDisable() {               //Plugin is being disabled
        getConfig().set("temporary",null);  //Remove temporary data
        this.saveConfig();                  //Save the configuration file
    }
    
    @Override                       //Replace Bukkit's own event
    public void onEnable() {        //Plugin was enabled
        this.saveDefaultConfig();   //Save the default configuration file if it's missing
        if (!hookVaultPlugin() ) {  //Couldn't hook Vault economy plugin?
            this.logger.severe(GetData.getMessagePrefix(false)+GetData.getConsoleMessage("economyMissing"));
            getServer().getPluginManager().disablePlugin(this);        //Disable the plugin if Vault can't bee hooked
            return;                 //Stop processing if Vault missing
        }                           //Continue if Vault is hooked successfully
        if (!setupPermissions()) {  //Try to access permissions through Vault
            //Failed to setup permissions -> send message to console
            this.logger.info(GetData.getMessagePrefix(false)+GetData.getConsoleMessage("permissionsMissing"));
        }
        Configuration.upgradeConfig();
        chatColorNormal = ChatColor.getByChar(getConfig().getString("colors.normal","f"));
        chatColorError  = ChatColor.getByChar(getConfig().getString("colors.error","c"));
        
        this.getServer().getScheduler().scheduleSyncRepeatingTask(this  //Schedule a new task
        ,   new Runnable() {                                            //Add command
                public void run() { Prices.updateSalesPrices(); }       //Call price update every time 
            }
        ,   getConfig().getLong("salesPrice.updateIntervalTicks")       //Delay (ticks) before first execution
        ,   getConfig().getLong("salesPrice.updateIntervalTicks")       //Delay (ticks) between executions
        );
        getCommand("ap").setExecutor(new Commands(this));               //Prepare to handle commands
        this.getServer().getPluginManager().registerEvents(this,this);  //Listen for events
        //Inform console that the plugin was successfully enabled
        this.logger.info( String.format(GetData.getConsoleMessage("loaded"),getDescription().getName(),getDescription().getVersion()));
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory()    == null) { return; }    //Player clicked outside inventory --> quit
        if (event.getCurrentItem()  == null) { return; }    //Player clicked outside inventory --> quit
        
        if (event.getInventory().getName().startsWith("AutoPrice shop")) {          //Player is in shop
            String shopName    = GetData.getShopForPlayer(event.getWhoClicked());   //Get shop name for specific player
            event.setCancelled(true);                                               //Don't let player move anything while in shop
            if (event.getCurrentItem().getType() == Material.LAVA) {                //Options item was clicked
                handleShopOptionsClick(event,shopName);
            } else if (event.getRawSlot() < 54) {                                   //Player clicked a slot of the shop
                if (event.getCurrentItem().getAmount() > 0) {                       //Empty slot
                    Trade.processPlayerPurchase(event, shopName);    //Player buying
                }
            } else {    //inventory slot clicked
                Trade.processPlayerSales(event, shopName);    //Player selling
            }
            Trade.setShopInfoOnStacks(event.getInventory(),true,true,shopName,(CommandSender) event.getWhoClicked());    //Add lores
            ((Player) event.getWhoClicked()).updateInventory();
        } 
    }   

    private void handleShopOptionsClick(InventoryClickEvent event, String shopName) {
        int pageToLoad  = getConfig().getInt("temporary.players."+event.getWhoClicked().getName()+".shopCurrentPageNumber",1);
        if (event.getClick() == ClickType.LEFT) {
            //load next page
            if (pageToLoad < getConfig().getInt("temporary.players."+event.getWhoClicked().getName()+".shopLastPageNumber",Integer.MAX_VALUE)) {
                pageToLoad += 1;
            }
        } else if (event.getClick() == ClickType.RIGHT) {
            //load previous page
            if (pageToLoad > 1) {
                pageToLoad -= 1;
            }
        } else if (event.getClick() == ClickType.SHIFT_LEFT) {
            changeSorting(event.getWhoClicked().getName());
        } else if (event.getClick() == ClickType.SHIFT_RIGHT) {
            changeCategory(event.getWhoClicked().getName(),shopName);
        }
        Trade.loadShopPage((CommandSender) event.getWhoClicked(), event.getInventory(), Integer.toString(pageToLoad), shopName);
    }

    private void changeCategory(String playerName, String shopName) {
        //change sorting (all --> custom categories --> uncategorized --> all)
        String currentCategory = getConfig().getString("temporary.players."+playerName+".category","all");
        List<String> categories = getConfig().getStringList("shops."+shopName+".categories");
        
        boolean selectNextCategory = false;
        if (currentCategory == "all") { 
            selectNextCategory = true; 
        } 
        if (currentCategory == "uncategorized") {
            getConfig().set("temporary.players."+playerName+".category",null);  //null=all
            return;
        }
        
        for (String category : categories) {
            if (selectNextCategory) { 
                getConfig().set("temporary.players."+playerName+".category",category);
                return;
            }
            if (category == currentCategory) { 
                selectNextCategory = true;
            }
        }
        
        getConfig().set("temporary.players."+playerName+".category","uncategorized");
    }
    
    private void changeSorting(String playerName) {
        //change sorting
        String sortBy = getConfig().getString("temporary.players."+playerName+".shopSortOrder","");
        switch (sortBy) {
            case "pp":
                getConfig().set("temporary.players."+playerName+".shopSortOrder","sp");
                break;
            case "sp":  
                getConfig().set("temporary.players."+playerName+".shopSortOrder",null);
                break;
            default: 
                getConfig().set("temporary.players."+playerName+".shopSortOrder","pp");
                break;
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onInventoryCloseEvent(InventoryCloseEvent event) {
        String shopName    = GetData.getShopForPlayer(event.getPlayer());
        Trade.setShopInfoOnStacks(event.getInventory(),true,false,shopName,(CommandSender) event.getPlayer());    //Remove lores
        ((Player) event.getPlayer()).updateInventory();
    }

    public void respondToSender(CommandSender sender, String message) {
        //Send a response to the command sender
        if (sender instanceof ConsoleCommandSender || sender instanceof Player) {
            //Sender is a console or player --> send the message
            sender.sendMessage(GetData.getMessagePrefix(true)+message);
        }
    }
        
    private boolean hookVaultPlugin() { //Hook Vault plugin (return true if successful)
        if (getServer().getPluginManager().getPlugin("Vault") == null) {    //Couldn't find Vault
            return false;               //Return true cause Vault was not found
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {              //RegisteredServiceProvider is missing
            return false;               //Return null cause the service is missing
        }
        economy = rsp.getProvider();    //Assign economy provider to public variable
        return economy != null;         //Return true if economy is not null and false if it is
    }
    
    public void updateItem(boolean isPlayerBuying, int amountTraded, ItemStack stack, float unitPrice, String shopName) {
        //Updates item(material) data/statistics
        String materialConfigPath = Configuration.getStackConfigPath(stack, shopName);
        
        if (isPlayerBuying) {    //Player has bought stuff
            Trade.addStockForPurchasePrice(stack,unitPrice,-1*amountTraded,shopName);
            //Set the number of price changes since last Player purchase to 0
            getConfig().set(materialConfigPath+".priceChecksSinceLastPurchase",0);
            //Update statistics
            getConfig().set(materialConfigPath+".totalMoneyFromPlayers",
                    getConfig().getDouble(materialConfigPath+".totalMoneyFromPlayers",0)+(amountTraded*unitPrice));
        } else {    //Player has sold stuff
            //Get purchase price (price for the items that were just sold can be purchased afterwards)
            float purchasePrice = Prices.getNewPurchasePrice(materialConfigPath, shopName, unitPrice);
            Trade.addStockForPurchasePrice( stack , purchasePrice , amountTraded*GetData.getStackConditionMultiplier(stack) , shopName );
            //Set the number of price changes since last player sale to 0
            getConfig().set(materialConfigPath+".priceChecksSinceLastPlayerSale",0);
            //Update statistics
            getConfig().set(materialConfigPath+".totalMoneyToPlayers",
                    getConfig().getDouble(materialConfigPath+".totalMoneyToPlayers",0)+(amountTraded*unitPrice));
            //Change price after sales to make sure players can't sell huge amounts if the price is too high
            Prices.decreaseSalesPriceIfNeed(materialConfigPath,stack,shopName);
        }
        this.saveConfig();    //Save the configuration file
    }
    
    private boolean setupPermissions()
    {    //Prepare to access permissions through Vault
        RegisteredServiceProvider<Permission> permissionProvider = 
                getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            permission = permissionProvider.getProvider();
        }
        return (permission != null);
    }
    
    public String formatStockAmount(int stockAmount) {
        //Format stock amount (add colors)
        if (stockAmount > 0) {                                      //Amount is positive
            return ChatColor.GREEN+""+stockAmount+chatColorNormal;  //Add green color
        } else {                                                    //Amount is not positive
            return ChatColor.RED+""+stockAmount+chatColorNormal;    //Add red color
        }
    }
    
    public String formatPrice(float price, char decimalSeparator, char groupingSeparator, 
            boolean separatorsFromConfig, boolean grouping, boolean foreZeroes) {
        
        return    formatPrice((double) price, decimalSeparator, groupingSeparator, separatorsFromConfig, grouping, foreZeroes);
    }
    
    public String formatPrice(double price, char decimalSeparator, char groupingSeparator, 
            boolean separatorsFromConfig, boolean grouping, boolean foreZeroes) {
        
        if (separatorsFromConfig == true ) {
            decimalSeparator    = (char) getConfig().getInt("decimalSeparatorASCII" ,46);
            groupingSeparator   = (char) getConfig().getInt("groupingSeparatorASCII",32);
        }
        
        DecimalFormatSymbols decimalFormatSymbols = new DecimalFormatSymbols(Locale.getDefault());
        decimalFormatSymbols.setDecimalSeparator(decimalSeparator);
        decimalFormatSymbols.setGroupingSeparator(groupingSeparator); 
        
        String formatString = "###,###,##0.00";
        
        if (!grouping)     { formatString = formatString.replace(",","");  }
        if (foreZeroes)    { formatString = formatString.replace("#","0"); }
        
        return new DecimalFormat(formatString,decimalFormatSymbols).format(price).trim();
    }
}    