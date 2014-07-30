package me.tubelius.autoprice;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

class Commands implements CommandExecutor {
    //Pointer to the class calling this class     
    private AutoPrice plugin;        
    Commands(AutoPrice plugin) { this.plugin = plugin; }
 
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (label.equalsIgnoreCase("AP") ) {                    //The main command
            if (args.length <= 0) {                             //No arguments at all
                plugin.respondToSender(sender,plugin.getData.getHelpMessage(sender) );    //Throw the help to the command sender
            } else if (args[0].equalsIgnoreCase("shop") ) {     //First parameter is "shop"
                openShop(sender,args);                        //Process the shop command
            } else if (args[0].equalsIgnoreCase("select") ) {   //First parameter is "select"
                setActiveShop(sender,args);                     //List shops or select active shop
            } else if (args[0].equalsIgnoreCase("name") ) {     //First parameter is "name"
                renameMaterial(sender,args);                        //Call for a function to rename a material
            } else if (args[0].equalsIgnoreCase("enable") ) {   //First parameter is "enable"
                setItemTradability(sender,args);                //Call for a function to enable the item
            } else if (args[0].equalsIgnoreCase("disable") ) {  //First parameter is "disable"
                setItemTradability(sender,args);                //Call for a function to disable the item
            } else if (args[0].equalsIgnoreCase("reload") ) {   //First parameter is "reload"
                handleReload(sender);                           //Call for a function reload the configuration file
            } else if (args[0].equalsIgnoreCase("save") ) {     //First parameter is "save"
                handleSave(sender);                             //Call for a function save the configuration file
            } else {    //First parameter is incorrect
                plugin.respondToSender(sender,plugin.chatColorError+
                        String.format(plugin.getData.getPlayerMessage("incorrectArgument", sender.getName())
                                ,   args[0],   "\n"+plugin.getData.getHelpMessage(sender)    )
                );
            }
        }
        plugin.saveConfig();
        return true;            //Completed successfully
    }
    
    private boolean setActiveShop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {  //Sender of the command is a player
            plugin.respondToSender(sender,plugin.chatColorError+plugin.getData.getPlayerMessage("ingameCmdOnly",sender.getName()));
            return false;   //shop was not selected
        }
        //List shops or select active shop
        if (args.length == 1) { //No arguments for this command --> list shops
            //loop all shops
            plugin.respondToSender(sender,
                    String.format(plugin.getData.getPlayerMessage("shops", sender.getName()), plugin.getData.getAvailableShops(sender)) );
        } else if (args.length >= 2) {  //Got a shop name --> try selecting it
            String shopName = getShopNameFromArgs(args);
            if (!plugin.getConfig().isConfigurationSection("shops."+shopName) ) {
                ChatColor chatColorCommand  = ChatColor.getByChar(plugin.getConfig().getString("colors.command","2") );
                plugin.respondToSender(sender,plugin.chatColorError+
                        String.format(plugin.getData.getPlayerMessage("invalidShop", sender.getName()),shopName,chatColorCommand+"/ap select"));
            } else if (!AutoPrice.permission.has(sender, "autoprice.shops."+shopName) ) {    //No permission
                plugin.respondToSender(sender,plugin.chatColorError+
                        String.format(plugin.getData.getPlayerMessage("noShopAccess", sender.getName()),shopName));
            } else {    //All OK --> select it
                plugin.getConfig().set("players."+sender.getName()+".shopName",shopName);
                plugin.respondToSender(sender, String.format(plugin.getData.getPlayerMessage("selectedShop", sender.getName()),shopName) );
                return true;    //successfully selected an active shop
            }
        } 
//        else {  //Invalid arguments
//            ChatColor chatColorCommand      = ChatColor.getByChar(plugin.getConfig().getString("colors.command","2") );
//            ChatColor chatColorParameter    = ChatColor.getByChar(plugin.getConfig().getString("colors.parameter","b") );
//            plugin.respondToSender(sender,plugin.chatColorError+String.format(plugin.getData.getPlayerMessage("invalidArguments"
//                    , sender.getName()),args[0],
//                    chatColorCommand+"/ap select"+plugin.chatColorError+"; "
//                    +chatColorCommand+"/ap select "+chatColorParameter+"shopname"+plugin.chatColorError));
//        }
        return false;   //shop was not selected
    }

    private String getShopNameFromArgs(String[] args) {
        String[] shopNameWords = Arrays.copyOfRange(args, 1, args.length);  //shop name starts from second argument
        String shopName = "";
        for (String shopNameWord : shopNameWords) {
            shopName += " "+shopNameWord;
        }
        return shopName.trim();
    }

    private void renameMaterial(CommandSender sender, String[] args) {
        if (sender instanceof Player) {     //Sender of the command is a player
            //Get colors
            ChatColor   chatColorCommand    = ChatColor.getByChar(plugin.getConfig().getString("colors.command","2") );
            ChatColor   chatColorParameter    = ChatColor.getByChar(plugin.getConfig().getString("colors.parameter","b") );
            //handle the command to rename a material
            if (!AutoPrice.permission.isEnabled() ) {    //Vault's plugin.permission hook is not enabled
                plugin.respondToSender(sender,plugin.chatColorError+plugin.getData.getPlayerMessage("noPermsCmdDisabled", sender.getName())); 
            } else if (!AutoPrice.permission.has(sender, "autoprice.rename") ) {    //plugin.permission missing
                plugin.respondToSender(sender,plugin.chatColorError+plugin.getData.getPlayerMessage("permission", sender.getName()));
            } else if (args.length != 2) { //Argument count is not 2
                plugin.respondToSender(sender,plugin.chatColorError+String.format(plugin.getData.getPlayerMessage("invalidArguments"
                                , sender.getName()),args[0],chatColorCommand+"/AP name "+chatColorParameter+"NewName"));
            } else if (args[1].contains(".") ) {
                plugin.respondToSender(sender,plugin.chatColorError+plugin.getData.getPlayerMessage("noDot", sender.getName()));
            } else {
                ItemStack stackInHand = ((HumanEntity) sender).getItemInHand().clone();
                if (stackInHand.getAmount() <= 0) {
                    plugin.respondToSender(sender,plugin.chatColorError+plugin.getData.getPlayerMessage("holdStack",sender.getName()));
                    return;
                }
                String shopName = plugin.getData.getShopForPlayer((HumanEntity) sender);
                String oldMaterialPath = plugin.configuration.getStackConfigPath(stackInHand, shopName);
                //Copy to new location
                plugin.configuration.moveConfigNode(oldMaterialPath, "shops."+shopName+".materials."+args[1], false, true);
                plugin.respondToSender(sender,
                    plugin.chatColorError+String.format(
                        plugin.getData.getPlayerMessage("renamed", sender.getName()) , oldMaterialPath.split("\\.")[3] , args[1]
                    )
                );
            }
        } else { plugin.respondToSender(sender,plugin.chatColorError+plugin.getData.getPlayerMessage("ingameCmdOnly",sender.getName())); }
    }
    
    private void handleReload(CommandSender sender) {
        //handle the command to reload config
        if (!AutoPrice.permission.isEnabled() ) {    //Vault's plugin.permission hook is not enabled
            plugin.respondToSender(sender,plugin.chatColorError+plugin.getData.getPlayerMessage("noPermsCmdDisabled",sender.getName()));
            return;    //Quit, can't access permissions
        }
        if (!AutoPrice.permission.has(sender, "autoprice.reload") ) {    //autoprice.reload permission missing
            plugin.respondToSender(sender,
                    plugin.chatColorError+String.format(plugin.getData.getPlayerMessage("permission",sender.getName()), "autoprice.reload")
            );
            return;    //No permission --> quit
        }
        plugin.reloadConfig();
        plugin.respondToSender(sender, plugin.getData.getPlayerMessage("configReloaded",sender.getName()) );
    }
    
    private void handleSave(CommandSender sender) {
        //handle the command to save configuration
        if (!AutoPrice.permission.isEnabled() ) {    //Vault's permission hook is not enabled
            plugin.respondToSender(sender,plugin.chatColorError+plugin.getData.getPlayerMessage("noPermsCmdDisabled",sender.getName()));
            return;    //Quit, can't access permissions
        }
        if (!AutoPrice.permission.has(sender, "autoprice.save") ) {    //autoprice.save permission missing
            plugin.respondToSender(sender,
                    plugin.chatColorError+String.format(plugin.getData.getPlayerMessage("permission",sender.getName()), "autoprice.save")
            );
            return;    //No permission --> quit
        }
        plugin.saveConfig();
        plugin.respondToSender(sender, plugin.getData.getPlayerMessage("configSaved",sender.getName()) );
    }
    
    private void setItemTradability(CommandSender sender, String[] args) {
        if (sender instanceof Player) {    //Sender of the command is a player
            ItemStack stackInHand = ((HumanEntity) sender).getItemInHand().clone();
            if (stackInHand.getAmount() <= 0) {
                plugin.respondToSender(sender,plugin.chatColorError+plugin.getData.getPlayerMessage("holdStack",sender.getName())); return;
            }
            String shopName    = plugin.getData.getShopForPlayer((HumanEntity) sender);
            if (args.length == 1) {        //Got one argument as we should
                if (args[0].equalsIgnoreCase("enable") ) {
                    if (AutoPrice.permission.has(sender, "autoprice.enableItems") ) {
                        enableMaterial(stackInHand, shopName, sender);
                    } else {
                        plugin.respondToSender(sender,
                                plugin.chatColorError+String.format(
                                        plugin.getData.getPlayerMessage("permission",sender.getName()) , "autoprice.enableItems")   );
                    }
                } else if (args[0].equalsIgnoreCase("disable") ) {
                    disableMaterial(stackInHand, shopName, sender);
                }
            } else {
                plugin.respondToSender(sender,plugin.chatColorError+String.format(plugin.getData.getPlayerMessage("invalidArguments"
                        , sender.getName()),args[0],plugin.getData.getHelpMessage(sender)));
            }
        } else {
            plugin.respondToSender(sender,plugin.chatColorError+plugin.getData.getPlayerMessage("ingameCmdOnly",sender.getName()));
        }
    }
    
    private void enableMaterial(ItemStack stackInHand, String shopName, CommandSender sender) {
        //Enable sub item
        String materialConfigPath = plugin.configuration.getStackConfigPath(stackInHand, shopName);
        String materialName;
        if (materialConfigPath != null) {
            plugin.getConfig().set(plugin.configuration.getStackConfigPath(stackInHand, shopName)+".tradable",true);
            materialName = plugin.getData.getInternalMaterialName(stackInHand, shopName);  //stackInHand.getType().name();
        } else {
            materialName = plugin.configuration.createMaterialConfiguration(stackInHand,shopName);
        }
        
        //Message player
        plugin.respondToSender(sender,String.format( plugin.getData.getPlayerMessage("materialEnabled",sender.getName()) ,materialName, shopName));
    }
    
    private void disableMaterial(ItemStack stackInHand, String shopName, CommandSender sender) {
        if (AutoPrice.permission.has(sender, "autoprice.disableItems") ) {
            //Disable sub item
            String materialPath = plugin.configuration.getStackConfigPath(stackInHand, shopName);
            if (materialPath != null) {
                plugin.getConfig().set(materialPath+".tradable",false);
                plugin.respondToSender(sender,String.format( plugin.getData.getPlayerMessage("materialDisabled",sender.getName()) 
                        ,   materialPath.split("\\.")[3] ,  shopName));
            } else {
                plugin.respondToSender(sender, plugin.getData.getPlayerMessage("noSuchItem",sender.getName()) );
            }
        } else {
            plugin.respondToSender(sender,  plugin.chatColorError+String.format(
                    plugin.getData.getPlayerMessage("permission",sender.getName()) , "autoprice.disableItems")  );
        }
    }

    private void openShop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {  //Sender of the command is a player
            plugin.respondToSender(sender,plugin.chatColorError+plugin.getData.getPlayerMessage("ingameCmdOnly",sender.getName()));
            return;
        }
        if (args.length >= 2) { //shop name was defined, try selecting it
            if (!setActiveShop(sender,args)) {
                return; //failed to select a shop
            }
        }
        String shopName         = plugin.getData.getShopForPlayer((HumanEntity) sender);
        Inventory shopInventory = Bukkit.createInventory((InventoryHolder) sender, 6*9, plugin.getData.getShopTitle(sender,shopName));
        ((HumanEntity) sender).openInventory(shopInventory);
        plugin.trade.loadShopPage(sender,shopInventory,"1",shopName);
        plugin.trade.setShopInfoOnStacks(shopInventory,true,true,shopName,sender);
    }
    
}