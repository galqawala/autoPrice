package me.tubelius.autoprice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginDescriptionFile;

public class GetData {
    //Pointer to the class calling this class
    private AutoPrice plugin;    
    public GetData(AutoPrice plugin) {
        this.plugin = plugin;
    }

    private String getInternalMaterialName(String materialConfigPath) {
        return materialConfigPath.split("\\.")[3];
    }
    public String getInternalMaterialName(ItemStack stack, String shopName) {
        return getInternalMaterialName(plugin.configuration.getStackConfigPath(stack,shopName));
    }

    public float getStackConditionMultiplier(ItemStack stack) {
        //Returns the condition value of a stack (for example: 0.1 = 10% left and 1 = no damage)
        if (stack.getType().getMaxDurability()>0) {                                 //Material uses durability
            float damage = (float) stack.getDurability();                           //Get amount of damage/durability in the item
            float materialMaxDuration = (float) stack.getType().getMaxDurability(); //Get the maximum durability of the material
            return (materialMaxDuration-damage)/materialMaxDuration;                //Return condition multiplier (between 0-1)
        } else {        //This material doesn't take damage (durability value may be used to set sub materials)
            return 1;   //Perfect condition
        }    
    }
    
    boolean loresMatch(String materialPath,ItemStack stack) {
        List<String> loresClean = new ArrayList<String>();
        if (stack != null) { 
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) { 
                if (meta.hasLore()) {
                    List<String> loresOriginal = stack.getItemMeta().getLore();
                    for (String lore : loresOriginal) {
                        if (!lore.startsWith("[AP]")) {
                            loresClean.add(lore);
                        }
                    }
                }
            }            
        }
        return loresClean.equals(plugin.getConfig().getStringList(materialPath+".lores"));
    }

    public String getMessagePrefix(Boolean colors) {
        //Prefix added to all messages the plugin sends
        PluginDescriptionFile pluginDescription = plugin.getDescription();    //Access the plugin description file
        if (colors) {    //Colors are allowed
            ChatColor chatColorPlugin = ChatColor.getByChar(plugin.getConfig().getString("colors.plugin","6"));
            return chatColorPlugin+pluginDescription.getName()+": "+plugin.chatColorNormal;
        } else {        //Colors are disabled
            return pluginDescription.getName() + ": ";    //A message prefix without colors
        }
    }
    
//    public float getTotalStockAmount(ItemStack stack, String shopName) {
//        return getTotalStockAmount(plugin.configuration.getStackConfigPath(stack, shopName));
//    }
//    
//    public float getTotalStockAmount(String materialConfigPath) {
//        //Total stock amount for given sub material
//        float stock = 0;
//
//        if (plugin.getConfig().isConfigurationSection(materialConfigPath+".stockPerPurchasePrice")) {
//            //Has stock for some purchase prices --> loop and find cheapest stock
//            for (String purchasePrice : 
//                plugin.getConfig().getConfigurationSection(materialConfigPath+".stockPerPurchasePrice").getKeys(false)) {
//                //Drop decimals (double --> integer --> float)
//                stock += (float) (int) plugin.getConfig().getDouble(materialConfigPath+".stockPerPurchasePrice."+purchasePrice,0);
//            }
//        }
//        
//        return stock;
//    }
    
    public int getTotalStockAmount(ItemStack stack, String shopName) {
        return getTotalStockAmount(plugin.configuration.getStackConfigPath(stack, shopName));
    }
    
    public int getTotalStockAmount(String materialConfigPath) {
        //Total stock amount for given sub material
        int stock = 0;

        if (plugin.getConfig().isConfigurationSection(materialConfigPath+".stockPerPurchasePrice")) {
            //Has stock for some purchase prices --> loop and find cheapest stock
            for (String purchasePrice : 
                plugin.getConfig().getConfigurationSection(materialConfigPath+".stockPerPurchasePrice").getKeys(false)) {
                //Drop decimals (double --> integer)
                stock += (int) plugin.getConfig().getDouble(materialConfigPath+".stockPerPurchasePrice."+purchasePrice,0);
            }
        }
        
        return stock;
    }
    
    public float getStockForPurchasePrice(String configDataPath, float purchasePrice) {
        if (purchasePrice == 0) {
            Thread.dumpStack();
        }
        return (float) plugin.getConfig().getDouble(configDataPath+".stockPerPurchasePrice."+plugin.formatPrice(purchasePrice,',',' ',false,false,false),0);
    }
    
    public String getHelpMessage(CommandSender sender) {
        ChatColor chatColorNormal       = ChatColor.getByChar(plugin.getConfig().getString("colors.normal","f"));
        ChatColor chatColorCommand      = ChatColor.getByChar(plugin.getConfig().getString("colors.command","2"));
        ChatColor chatColorParameter    = ChatColor.getByChar(plugin.getConfig().getString("colors.parameter","b"));
        
        String helpMessage = chatColorNormal+String.format("\n"+getPlayerMessage("help",sender.getName())
                ,   chatColorCommand+"/AP shop \n"+chatColorNormal
                ,   chatColorCommand+"/AP select\n"+chatColorNormal
                ,   chatColorCommand+"/AP select "+chatColorParameter+"shopname\n"+chatColorNormal)+"\n";
        
        if (AutoPrice.permission.isEnabled()) {    //Got access to permissions required for these commands
            //Show help for commands that command sender has permission to
            if (AutoPrice.permission.has(sender, "autoprice.rename")) {
                helpMessage += chatColorCommand+"/AP name "+chatColorParameter+" NewName\n";
            } if (AutoPrice.permission.has(sender, "autoprice.enableItems")) {
                helpMessage += chatColorCommand+"/AP enable"+chatColorParameter+"\n";
            } if (AutoPrice.permission.has(sender, "autoprice.disableItems")) {
                helpMessage += chatColorCommand+"/AP disable"+chatColorParameter+"\n";
            } if (AutoPrice.permission.has(sender, "autoprice.save")) {
                helpMessage += chatColorCommand+"/AP save\n";
            } if (AutoPrice.permission.has(sender, "autoprice.reload")) {
                helpMessage += chatColorCommand+"/AP reload\n";
            }
        }
        
        return helpMessage;
    }

    public boolean isTradable(ItemStack stack, String shopName) {
        return isTradable(plugin.configuration.getStackConfigPath(stack, shopName));
    }

    public boolean isTradable(String materialConfigPath) {
        //Returns true if a stack is tradable and false if not
        if (materialConfigPath == null) {
            return false;    //Missing parameter
        }
        if (!plugin.getConfig().getBoolean(materialConfigPath+".tradable",true)) {
            return false;    //Sub material is disabled
        }
        return true;    //It's tradable! 
    }
    
    public boolean mainItemHasTradableSubItems(String mainMaterialName, String shopName) {
        //loop materials in configuration -> return matching material's path
        for (String materialNode : plugin.getConfig().getConfigurationSection("shops."+shopName+".materials").getKeys(false)) {
            String materialPath = "shops."+shopName+".materials."+materialNode;
            if ( mainMaterialName.equalsIgnoreCase(plugin.getConfig().getString(materialPath+".mainMaterial"))) {
                //main material matches -> check if this material is tradable 
                if (plugin.getConfig().getBoolean(materialPath+".tradable",true)) {
                    return true;    //This main material has at least one tradable sub material
                }
            }
        }
        return false;    //No tradable sub materials were found 
    }
    
    public ItemStack getInventoryLastItemStack(CommandSender sender, Inventory inventory) {
        ItemStack lastStack = null;
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null) {
                if (itemStack.getAmount()>0) { lastStack = itemStack; }
            }
        }
        return lastStack;
    }
    
    public int getMaximumAmountPlayerCanBuy(CommandSender sender, String materialConfigPath, String shopName) {
        //Not tradable          --> 0
        if (isTradable(materialConfigPath) == false) { return 0; }
        //No permission         --> 0
        if (!AutoPrice.permission.has(sender, "autoprice.shops."+shopName+".buy.material."+getInternalMaterialName(materialConfigPath)) ) { return 0; }
        //No inventory space    --> 0
        if ( ((HumanEntity) sender).getInventory().firstEmpty() < 0) { return 0; }
        //Maximum stack size (material name is in 4th position in configuration path)
        String mainMaterial     = plugin.getConfig().getString(materialConfigPath+".mainMaterial");
        int amountPlayerCanBuy  = Material.getMaterial(mainMaterial).getMaxStackSize();
        //How many the player affords
        float cheapestPurchasePrice = plugin.prices.getMinimumPurchasePrice(materialConfigPath);
        double playerBalance        = AutoPrice.economy.getBalance((OfflinePlayer) sender);
        int amountPlayerAffords = (int) Math.floor(playerBalance/cheapestPurchasePrice);
        
        if (amountPlayerCanBuy > amountPlayerAffords) { amountPlayerCanBuy = amountPlayerAffords; }
        //How many in the stock
        int stockAmountForCheapestPrice    = (int) Math.floor(getStockForPurchasePrice(materialConfigPath,cheapestPurchasePrice));
        if (amountPlayerCanBuy > stockAmountForCheapestPrice) { amountPlayerCanBuy = stockAmountForCheapestPrice; }
        //Return result
        return amountPlayerCanBuy;
    }

    public int getAmountToMove(ClickType clickType) {
        if (clickType == ClickType.LEFT) {
            return    1;
        } else if (clickType == ClickType.RIGHT) {
            return    4;
        } else if (clickType == ClickType.SHIFT_LEFT) {
            return    16;
        } else if (clickType == ClickType.SHIFT_RIGHT) {
            return    64;
        } else {
            return    0;
        }
    }

    public int getMaximumAmountPlayerCanSell(CommandSender sender, String materialConfigPath, ItemStack stack, String shopName) {
        //Not tradable             --> 0
        if (isTradable(materialConfigPath) == false) { return 0; }
        //No permission         --> 0
        if (!AutoPrice.permission.has(sender, "autoprice.shops."+shopName+".sell.material."+getInternalMaterialName(materialConfigPath)) ) { return 0; }

        int amountPlayerCanSell = stack.getType().getMaxStackSize();
        //Limits the stack amount by the maximum allowed sales amount 
        float salesUnitPrice        = plugin.prices.getFinalPrice(false,stack,shopName);
        float totalMoneyFromPlayers = (float) plugin.getConfig().getDouble(materialConfigPath+".totalMoneyFromPlayers"    ,0);
        float totalMoneyToPlayers     = (float) plugin.getConfig().getDouble(materialConfigPath+".totalMoneyToPlayers"    ,0);

        if (salesUnitPrice > plugin.configuration.getMaterialConfigDouble(shopName, materialConfigPath, "salesPrice.minPrice")) {
//            Sales price is higher than minimum --> limit amount sold if we are making losses
//            Maximum sales quantity is the amount that would cause sales price to decrease 0.01
            
//            How much should totalMoneyToPlayers be to cause sales price to drop at least 0.01?  
            float totalMoneyToPlayersNew = (salesUnitPrice * totalMoneyFromPlayers) / (salesUnitPrice - (float) 0.01);
//            How many units could you sell to make sales price drop that 0.01?
            int maximumSalesAmount = (int) Math.ceil( (totalMoneyToPlayersNew-totalMoneyToPlayers)/salesUnitPrice );
//            You could always sell at least 1 unit
            if (maximumSalesAmount<1) { maximumSalesAmount=1; }
//            Limit sales amount if it's above the limit
            if (amountPlayerCanSell > maximumSalesAmount) {
                amountPlayerCanSell = maximumSalesAmount;
            }
        }
        return amountPlayerCanSell;
    }
    
    public String[][] getTradableSubMaterials(CommandSender sender, String shopName) {
        //Returns tradable sub materials (sorted if needed)
        //0 = main material, 1 = sub material, 2 = configuration path, 3 = purchase price, 4 = sales price, 5 = page number
        String[][]         tradableSubMaterials    = new String[10000][6];
        int itemIndex = 0;
        //Loop main&sub materials --> save if tradable
        for (String materialNode : plugin.getConfig().getConfigurationSection("shops."+shopName+".materials").getKeys(false)) {
            String materialPath     =   "shops."+shopName+".materials."+materialNode;
            String mainMaterialName =   plugin.getConfig().getString(materialPath+".mainMaterial");
            if (isValidMaterialName(mainMaterialName) && materialInRequestedCategory(sender,materialPath) 
                    && materialMatchesFilter(sender,materialPath)) {
                
                if (plugin.getConfig().getBoolean(materialPath+".tradable",true)) {
                    tradableSubMaterials[itemIndex][0] = mainMaterialName;
                    tradableSubMaterials[itemIndex][1] = plugin.getConfig().getString(materialPath+".subMaterial");
                    tradableSubMaterials[itemIndex][2] = materialPath;
                    tradableSubMaterials[itemIndex][3] = 
                            plugin.formatPrice(plugin.prices.getMinimumPurchasePrice(materialPath),'.',' ',false,false,true);
                    tradableSubMaterials[itemIndex][4] = plugin.formatPrice(  
                            plugin.configuration.getMaterialConfigDouble(shopName, materialPath, "salesPrice.price")  
                        ,',',' ',false,false,true);
                    itemIndex += 1;
                }
            }
        }
        if (sender != null) {    //Players request
            tradableSubMaterials = sortMaterials(sender,tradableSubMaterials);
            addPageNumbersToMaterialList(sender,tradableSubMaterials,shopName);
        } 
        return tradableSubMaterials;                                //Return item array
    }
    
    private boolean materialInRequestedCategory(CommandSender sender, String materialPath) {
        if (sender == null) {   
            return  true;   
        } 
        String currentCategory = plugin.getConfig().getString("temporary.players."+sender.getName()+".category","all");
        if (currentCategory=="all") { 
            return  true; 
        } 
        List<String> materialCategories = plugin.getConfig().getStringList(materialPath+".categories");
        if (currentCategory=="uncategorized" && materialCategories.isEmpty()) { 
            return  true; 
        } 
        if (materialCategories.contains(currentCategory)) { 
            return  true; 
        } 
        return  false;
    }
    
    private boolean materialMatchesFilter(CommandSender sender, String materialPath) {
        if (sender == null) {   
            return  true;   
        } 
        String filter = plugin.getConfig().getString("temporary.players."+sender.getName()+".shopFilter");
        if (filter == null) { 
            return  true; 
        }
        int stockAmount = getTotalStockAmount(materialPath);
        if (filter == "hasStock" && stockAmount >= 1) { 
            return  true; 
        } else if (filter == "noStock" && stockAmount < 1) { 
            return  true; 
        } 
        return  false;
    }
    
    public String[][] addPageNumbersToMaterialList(CommandSender sender, String[][] tradableSubMaterials, String shopName) {
        int shopItemsPerPage   =   plugin.getConfig().getInt("shops."+shopName+".shopItemsPerPage" , plugin.getConfig().getInt("shopItemsPerPage"));
        int itemNo             =   0;
        for (String[] subMaterial : tradableSubMaterials) {
            if (subMaterial[0] == null) { break; } //Check until null is encountered
            itemNo += 1;
            subMaterial[5] = Integer.toString((int) Math.ceil((float) itemNo / (float) shopItemsPerPage));
        }
        plugin.getConfig().set("temporary.players."+sender.getName()+".shopLastPageNumber"
                ,(int) Math.ceil((float) itemNo / (float) shopItemsPerPage));
        return  tradableSubMaterials;
    }
    
    private boolean isValidMaterialName(String mainMaterialName) {
        //Is material supported in current version?
        if (Material.getMaterial(mainMaterialName) != null) {
            return  true;
        }
        return  false;
    }

    public String[][] sortMaterials(CommandSender sender, String[][] tradableSubMaterials) {
        String sortBy = plugin.getConfig().getString("temporary.players."+sender.getName()+".shopSortOrder","");
        
        if (sortBy.equalsIgnoreCase("PP")) {    //Sort by purchase price ascending
            Arrays.sort(tradableSubMaterials, new Comparator<String[]>() {
                @Override
                public int compare(String[] s1, String[] s2) {
                    if (s1[3] == null) {
                        return 1;
                    } else if (s2[3] == null) {
                        return -1;
                    } else {
                        return s1[3].compareTo(s2[3]);  //Ascending
                    }
                }
            });
        } else if (sortBy.equalsIgnoreCase("SP")) { //Sort by purchase price descending
            Arrays.sort(tradableSubMaterials, new Comparator<String[]>() {
                @Override
                public int compare(String[] s1, String[] s2) {
                    if (s1[4] == null) {
                        return 1;
                    } else if (s2[4] == null) {
                        return -1;
                    } else {
                        return s2[4].compareTo(s1[4]);  //Descending
                    }
                }
            });
        }
        
        return tradableSubMaterials;
    }

    public String getShopForPlayer(HumanEntity player) {
        //Get the name of active shop for specific player
        String shopName = plugin.getConfig().getString("players."+player.getName()+".shopName","default");
        if (plugin.getConfig().isConfigurationSection("shops."+shopName) && 
                AutoPrice.permission.has((CommandSender) player, "autoprice.shops."+shopName)) {
            //Shop exists and player has permissions for it --> return it
            return  shopName;
        } else {    //Shop missing or no permissions --> return first available shop
            for (String shopNameFromList : plugin.getConfig().getConfigurationSection("shops").getKeys(false)) {
                if (AutoPrice.permission.has((CommandSender) player, "autoprice.shops."+shopNameFromList)) {
                    //Shop exists and player has permissions for it --> return it
                    return  shopNameFromList;
                }
            }    
        }
        return "default";
    }

    
    public String getConsoleMessage(String messageId) {
        String language = plugin.getConfig().getString("consoleLanguage","english");
        return plugin.getConfig().getString("languages."+language+"."+messageId);
    }
    
    public String getPlayerMessage(String messageId, String playerName) {
        String language = plugin.getConfig().getString("players."+playerName+".language"
                ,   plugin.getConfig().getString("defaultPlayerLanguage","english"));
        return plugin.getConfig().getString("languages."+language+"."+messageId);
    }
    
    public List<String> getPlayerLanguageStringList(String messageId, String playerName) {
        String language = plugin.getConfig().getString("players."+playerName+".language"
                ,   plugin.getConfig().getString("defaultPlayerLanguage","english"));
        return plugin.getConfig().getStringList("languages."+language+"."+messageId);
    }
    
    public String getAvailableShops(CommandSender sender) {
        String shopList = "";
        for (String shopName : plugin.getConfig().getConfigurationSection("shops").getKeys(false) ) {
            if (AutoPrice.permission.has(sender, "autoprice.shops."+shopName) ) {    //Player has permission to this shop --> list it
                if (shopList.length() > 0) { shopList += ", "; }
                shopList += shopName;
            } 
        }
        return shopList;
    }
}