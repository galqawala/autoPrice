package me.tubelius.autoprice;

//import java.util.ArrayList;
//import java.util.Iterator;
//import java.util.List;
//import java.util.Map;
//import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
//import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
//import org.bukkit.inventory.meta.ItemMeta;
//import org.bukkit.inventory.meta.LeatherArmorMeta;

public class Commands implements CommandExecutor {
    //Pointer to the class calling this class     
    private AutoPrice Plugin;        
    public Commands(AutoPrice Plugin) { this.Plugin = Plugin; }
 
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (label.equalsIgnoreCase("AP") ) {                    //The main command
            if (args.length <= 0) {                             //No arguments at all
                Plugin.respondToSender(sender,Plugin.GetData.getHelpMessage(sender) );    //Throw the help to the command sender
            } else if (args[0].equalsIgnoreCase("shop") ) {     //First parameter is "shop"
                handleShop(sender,args);                        //Process the shop command
            } else if (args[0].equalsIgnoreCase("select") ) {   //First parameter is "select"
                setActiveShop(sender,args);                     //List shops or select active shop
            } else if (args[0].equalsIgnoreCase("name") ) {     //First parameter is "name"
                handleName(sender,args);                        //Call for a function to rename a material
            } else if (args[0].equalsIgnoreCase("enable") ) {   //First parameter is "enable"
                setItemTradability(sender,args);                //Call for a function to enable the item
            } else if (args[0].equalsIgnoreCase("disable") ) {  //First parameter is "disable"
                setItemTradability(sender,args);                //Call for a function to disable the item
            } else if (args[0].equalsIgnoreCase("reload") ) {   //First parameter is "reload"
                handleReload(sender);                           //Call for a function reload the configuration file
            } else if (args[0].equalsIgnoreCase("save") ) {     //First parameter is "save"
                handleSave(sender);                             //Call for a function save the configuration file
            } else {    //First parameter is incorrect
                Plugin.respondToSender(sender,Plugin.chatColorError+
                        String.format(Plugin.GetData.getPlayerMessage("incorrectArgument", sender.getName())
                                ,   args[0],   "\n"+Plugin.GetData.getHelpMessage(sender)    )
                );
            }
        }
        Plugin.saveConfig();
        return true;            //Completed successfully
    }
    
    private void setActiveShop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {  //Sender of the command is a player
            Plugin.respondToSender(sender,Plugin.chatColorError+Plugin.GetData.getPlayerMessage("ingameCmdOnly",sender.getName()));
            return;
        }
        //List shops or select active shop
        if (args.length == 1) { //No arguments for this command --> list shops
            //loop all shops
            Plugin.respondToSender(sender,
                    String.format(Plugin.GetData.getPlayerMessage("shops", sender.getName()), Plugin.GetData.getAvailableShops(sender)) );
        } else if (args.length == 2) {  //Shop named --> try selecting it
            if (!Plugin.getConfig().isConfigurationSection("shops."+args[1]) ) {
                ChatColor chatColorCommand  = ChatColor.getByChar(Plugin.getConfig().getString("colors.command","2") );
                Plugin.respondToSender(sender,Plugin.chatColorError+
                        String.format(Plugin.GetData.getPlayerMessage("invalidShop", sender.getName()),args[1],chatColorCommand+"/ap select"));
            } else if (!AutoPrice.permission.has(sender, "autoprice.shops."+args[1]) ) {    //No permission
                Plugin.respondToSender(sender,Plugin.chatColorError+
                        String.format(Plugin.GetData.getPlayerMessage("noShopAccess", sender.getName()),args[1]));
            } else {    //All OK --> select it
                Plugin.getConfig().set("players."+sender.getName()+".shopName",args[1]);
                Plugin.respondToSender(sender, String.format(Plugin.GetData.getPlayerMessage("selectedShop", sender.getName()),args[1]) );
            }
        } else {  //Invalid arguments
            ChatColor chatColorCommand      = ChatColor.getByChar(Plugin.getConfig().getString("colors.command","2") );
            ChatColor chatColorParameter    = ChatColor.getByChar(Plugin.getConfig().getString("colors.parameter","b") );
            Plugin.respondToSender(sender,Plugin.chatColorError+String.format(Plugin.GetData.getPlayerMessage("invalidArguments"
                    , sender.getName()),args[0],
                    chatColorCommand+"/ap select"+Plugin.chatColorError+"; "
                    +chatColorCommand+"/ap select "+chatColorParameter+"shopname"+Plugin.chatColorError));
        }
    }

    public void handleName(CommandSender sender, String[] args) {
        if (sender instanceof Player) {     //Sender of the command is a player
            //Get colors
            ChatColor   chatColorCommand    = ChatColor.getByChar(Plugin.getConfig().getString("colors.command","2") );
            ChatColor   chatColorParameter    = ChatColor.getByChar(Plugin.getConfig().getString("colors.parameter","b") );
            //handle the command to rename a material
            if (!AutoPrice.permission.isEnabled() ) {    //Vault's plugin.permission hook is not enabled
                Plugin.respondToSender(sender,Plugin.chatColorError+Plugin.GetData.getPlayerMessage("noPermsCmdDisabled", sender.getName())); 
            } else if (!AutoPrice.permission.has(sender, "autoprice.rename") ) {    //plugin.permission missing
                Plugin.respondToSender(sender,Plugin.chatColorError+Plugin.GetData.getPlayerMessage("permission", sender.getName()));
            } else if (args.length != 2) { //Argument count is not 2
                Plugin.respondToSender(sender,Plugin.chatColorError+String.format(Plugin.GetData.getPlayerMessage("invalidArguments"
                                , sender.getName()),args[0],chatColorCommand+"/AP name "+chatColorParameter+"NewName"));
            } else if (args[1].contains(".") ) {
                Plugin.respondToSender(sender,Plugin.chatColorError+Plugin.GetData.getPlayerMessage("noDot", sender.getName()));
            } else {
                ItemStack stackInHand = ((HumanEntity) sender).getItemInHand().clone();
                if (stackInHand.getAmount() <= 0) {
                    Plugin.respondToSender(sender,Plugin.chatColorError+Plugin.GetData.getPlayerMessage("holdStack",sender.getName()));
                    return;
                }
                String shopName = Plugin.GetData.getShopForPlayer((HumanEntity) sender);
                String oldMaterialPath = Plugin.Configuration.getStackConfigPath(stackInHand, shopName);
                //Copy to new location
                Plugin.Configuration.moveConfigNode(oldMaterialPath, "shops."+shopName+".materials."+args[1], false, true);
                Plugin.respondToSender(sender,
                    Plugin.chatColorError+String.format(
                        Plugin.GetData.getPlayerMessage("renamed", sender.getName()) , oldMaterialPath.split("\\.")[3] , args[1]
                    )
                );
            }
        } else { Plugin.respondToSender(sender,Plugin.chatColorError+Plugin.GetData.getPlayerMessage("ingameCmdOnly",sender.getName())); }
    }
    
    public void handleReload(CommandSender sender) {
        //handle the command to reload config
        if (!AutoPrice.permission.isEnabled() ) {    //Vault's plugin.permission hook is not enabled
            Plugin.respondToSender(sender,Plugin.chatColorError+Plugin.GetData.getPlayerMessage("noPermsCmdDisabled",sender.getName()));
            return;    //Quit, can't access permissions
        }
        if (!AutoPrice.permission.has(sender, "autoprice.reload") ) {    //autoprice.reload permission missing
            Plugin.respondToSender(sender,
                    Plugin.chatColorError+String.format(Plugin.GetData.getPlayerMessage("permission",sender.getName()), "autoprice.reload")
            );
            return;    //No permission --> quit
        }
        Plugin.reloadConfig();
        Plugin.respondToSender(sender, Plugin.GetData.getPlayerMessage("configReloaded",sender.getName()) );
    }
    
    public void handleSave(CommandSender sender) {
        //handle the command to save configuration
        if (!AutoPrice.permission.isEnabled() ) {    //Vault's permission hook is not enabled
            Plugin.respondToSender(sender,Plugin.chatColorError+Plugin.GetData.getPlayerMessage("noPermsCmdDisabled",sender.getName()));
            return;    //Quit, can't access permissions
        }
        if (!AutoPrice.permission.has(sender, "autoprice.save") ) {    //autoprice.save permission missing
            Plugin.respondToSender(sender,
                    Plugin.chatColorError+String.format(Plugin.GetData.getPlayerMessage("permission",sender.getName()), "autoprice.save")
            );
            return;    //No permission --> quit
        }
        Plugin.saveConfig();
        Plugin.respondToSender(sender, Plugin.GetData.getPlayerMessage("configSaved",sender.getName()) );
    }
    
    public void setItemTradability(CommandSender sender, String[] args) {
        if (sender instanceof Player) {    //Sender of the command is a player
            ItemStack stackInHand = ((HumanEntity) sender).getItemInHand().clone();
            if (stackInHand.getAmount() <= 0) {
                Plugin.respondToSender(sender,Plugin.chatColorError+Plugin.GetData.getPlayerMessage("holdStack",sender.getName())); return;
            }
            String shopName    = Plugin.GetData.getShopForPlayer((HumanEntity) sender);
            if (args.length == 1) {        //Got one argument as we should
                if (args[0].equalsIgnoreCase("enable") ) {
                    if (AutoPrice.permission.has(sender, "autoprice.enableItems") ) {
                        enableMaterial(stackInHand, shopName, sender);
                    } else {
                        Plugin.respondToSender(sender,
                                Plugin.chatColorError+String.format(
                                        Plugin.GetData.getPlayerMessage("permission",sender.getName()) , "autoprice.enableItems")   );
                    }
                } else if (args[0].equalsIgnoreCase("disable") ) {
                    disableMaterial(stackInHand, shopName, sender);
                }
            } else {
                Plugin.respondToSender(sender,Plugin.chatColorError+String.format(Plugin.GetData.getPlayerMessage("invalidArguments"
                        , sender.getName()),args[0],Plugin.GetData.getHelpMessage(sender)));
            }
        } else {
            Plugin.respondToSender(sender,Plugin.chatColorError+Plugin.GetData.getPlayerMessage("ingameCmdOnly",sender.getName()));
        }
    }
    
    private void enableMaterial(ItemStack stackInHand, String shopName, CommandSender sender) {
        //Enable sub item
        String materialConfigPath = Plugin.Configuration.getStackConfigPath(stackInHand, shopName);
        String materialName;
        if (materialConfigPath != null) {
            Plugin.getConfig().set(Plugin.Configuration.getStackConfigPath(stackInHand, shopName)+".tradable",true);
            materialName = Plugin.GetData.getInternalMaterialName(stackInHand, shopName);  //stackInHand.getType().name();
        } else {
            materialName = Plugin.Configuration.createMaterialConfiguration(stackInHand,shopName);
        }
        
        //Message player
        Plugin.respondToSender(sender,String.format( Plugin.GetData.getPlayerMessage("materialEnabled",sender.getName()) ,materialName, shopName));
    }
    
    private void disableMaterial(ItemStack stackInHand, String shopName, CommandSender sender) {
        if (AutoPrice.permission.has(sender, "autoprice.disableItems") ) {
            //Disable sub item
            String materialPath = Plugin.Configuration.getStackConfigPath(stackInHand, shopName);
            if (materialPath != null) {
                Plugin.getConfig().set(materialPath+".tradable",false);
                Plugin.respondToSender(sender,String.format( Plugin.GetData.getPlayerMessage("materialDisabled",sender.getName()) 
                        ,   materialPath.split("\\.")[3] ,  shopName));
            } else {
                Plugin.respondToSender(sender, Plugin.GetData.getPlayerMessage("noSuchItem",sender.getName()) );
            }
        } else {
            Plugin.respondToSender(sender,  Plugin.chatColorError+String.format(
                    Plugin.GetData.getPlayerMessage("permission",sender.getName()) , "autoprice.disableItems")  );
        }
    }

    public void handleShop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {  //Sender of the command is a player
            Plugin.respondToSender(sender,Plugin.chatColorError+Plugin.GetData.getPlayerMessage("ingameCmdOnly",sender.getName()));
            return;
        }
        String shopName         = Plugin.GetData.getShopForPlayer((HumanEntity) sender);
        Inventory shopInventory = Bukkit.createInventory((InventoryHolder) sender, 6*9, "AutoPrice shop: "+shopName);
        ((HumanEntity) sender).openInventory(shopInventory);
        Plugin.Trade.loadShopPage(sender,shopInventory,"1",shopName);
        Plugin.Trade.setShopInfoOnStacks(shopInventory,true,true,shopName,sender);
    }
    
}