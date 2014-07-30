package me.tubelius.autoprice;

import java.util.ArrayList;
//import java.util.Arrays;
import java.util.List;

import net.milkbowl.vault.economy.EconomyResponse;

import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
//import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

public class Trade {
    //Pointer to the class calling this class     
    private AutoPrice plugin;        
    public Trade(AutoPrice plugin) { this.plugin = plugin; }

    public void addStockForPurchasePrice(ItemStack stack, float purchasePrice, float amountToAdd, String shopName) {
        String configPath = plugin.configuration.getStackConfigPath(stack, shopName);
        
        float stockAmount = plugin.getData.getStockForPurchasePrice(configPath,purchasePrice);
        stockAmount += amountToAdd;
        
        if (stockAmount > 0) {    //Update stock
            plugin.getConfig().set(configPath+".stockPerPurchasePrice."+plugin.formatPrice(purchasePrice,',',' ',false,false,false) , stockAmount);
        } else {    //Remove purchase price
            plugin.getConfig().set(configPath+".stockPerPurchasePrice."+plugin.formatPrice(purchasePrice,',',' ',false,false,false) , null);
        }
    }

    public void loadShopPage(CommandSender sender, Inventory shopInventory, String pageNumberToLoad, String shopName) {
        shopInventory.clear();
        String[][] materials = plugin.getData.getTradableSubMaterials(sender, shopName);    //updates page count too
        shopInventory.setItem(plugin.getConfig().getInt("shops."+shopName+".optionsItemLocation" , plugin.getConfig().getInt("optionsItemLocation"))
                , getShopOptionsItem(sender,pageNumberToLoad)); //requires page count (run getTradableSubMaterials first) 

        for (String[] subMaterial : materials) {
            if (subMaterial[0] != null) {
                //0 = main material, 1 = sub material, 2 = config path, 3 = purchase price, 4 = sales price, 5 = page number

                //Add item if it belongs to page being loaded
                if (subMaterial[5].equalsIgnoreCase(pageNumberToLoad)) {
                    int amount = plugin.getData.getMaximumAmountPlayerCanBuy(sender,subMaterial[2],shopName);
                    ItemStack stackToBuy = null;
                    if (amount >= 1) {
                        stackToBuy  = new ItemStack(Material.getMaterial(subMaterial[0]), amount);
                    } else {
                        stackToBuy  = new ItemStack(Material.getMaterial(subMaterial[0]), 1);
                    }
                    
                    //Prepare the stack
                    if (NumberUtils.isNumber(subMaterial[1])) { stackToBuy.setDurability(Short.parseShort(subMaterial[1])); }
                    setStackMeta(stackToBuy,subMaterial[2]);
                    //Add to shop
                    shopInventory.addItem(stackToBuy);
                }
            } else {  //Ran out of materials
                break;
            }
        }
        plugin.getConfig().set("temporary.players."+sender.getName()+".shopCurrentPageNumber",Integer.parseInt(pageNumberToLoad));
    }
    
    private ItemStack getShopOptionsItem(CommandSender sender, String pageNumberToLoad) {
        //options item (to-do: left click = next page, right click = previous page, shift+LMB = change sorting, shift+RMB = change category)
        ItemStack stack = new ItemStack(Material.LAVA, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName("[AP] "+plugin.getData.getPlayerMessage("options", sender.getName()));
        //static lores/tooltips
        List<String> lores = new ArrayList<String>();
        String lastPage = plugin.getConfig().getString("temporary.players."+sender.getName()+".shopLastPageNumber", 
                Integer.toString(Integer.MAX_VALUE) );
        lores.add( String.format(plugin.getData.getPlayerMessage("optionsPage", sender.getName()),pageNumberToLoad,lastPage) );
        //sorting
        String sortBy = plugin.getConfig().getString("temporary.players."+sender.getName()+".shopSortOrder","default");
        if (sortBy=="sp")       { sortBy = plugin.getData.getPlayerMessage("sortingSP", sender.getName()); }
        else if (sortBy=="pp")  { sortBy = plugin.getData.getPlayerMessage("sortingPP", sender.getName()); }
        lores.add( String.format(plugin.getData.getPlayerMessage("optionsSorting", sender.getName()),sortBy) );
        //category
        String currentCategory = plugin.getConfig().getString("temporary.players."+sender.getName()+".category","all");
        lores.add( String.format(plugin.getData.getPlayerMessage("optionsCategory", sender.getName()),currentCategory) );
        //filter
        String filter = plugin.getConfig().getString("temporary.players."+sender.getName()+".shopFilter","default");
        String filterText = filter;
        if (filter=="noStock")          { filterText = plugin.getData.getPlayerMessage("filterNoStock",    sender.getName()); }
        else if (filter=="hasStock")    { filterText = plugin.getData.getPlayerMessage("filterHasStock",   sender.getName()); }
        else                            { filterText = plugin.getData.getPlayerMessage("filterNotSet",     sender.getName()); }
        lores.add( String.format(plugin.getData.getPlayerMessage("optionsFilter", sender.getName()),filterText) );
        //update
        lores.addAll( plugin.getData.getPlayerLanguageStringList("optionsLores", sender.getName()) );
        meta.setLore(lores);
        stack.setItemMeta(meta);
        return stack;
    }

    private void setStackMeta(ItemStack stack, String materialPath) {
        ItemMeta meta = stack.getItemMeta();
        //display name
        String displayName = plugin.getConfig().getString(materialPath+".displayName");
        if (displayName != null) {
            meta.setDisplayName(displayName);
        }
        //lores
        if (plugin.getConfig().isList(materialPath+".lores")) {
            meta.setLore(plugin.getConfig().getStringList(materialPath+".lores"));
        }
        //color
        if (plugin.getConfig().isColor(materialPath+".color")) {
            if (meta instanceof LeatherArmorMeta) {
                //LeatherArmorMeta color
                LeatherArmorMeta leatherArmorMeta = (LeatherArmorMeta)meta;
                leatherArmorMeta.setColor(plugin.getConfig().getColor(materialPath+".color"));
                meta = leatherArmorMeta;
            }
        }
        //save meta
        stack.setItemMeta(meta);
        //enchants
        if (plugin.getConfig().isConfigurationSection(materialPath+".enchantments")) {
            for (String enchantmentName : plugin.getConfig().getConfigurationSection(materialPath+".enchantments").getKeys(false)) {
                stack.addUnsafeEnchantment(Enchantment.getByName(enchantmentName), 
                        plugin.getConfig().getInt(materialPath+".enchantments."+enchantmentName));
            }
            
        }
    }

    private void addLore(ItemStack stack, String lore, boolean autoPricePrefix) {
        ItemMeta meta = stack.getItemMeta();
        
        List<String> lores;
        if (stack.getItemMeta().hasLore()) {
            lores = stack.getItemMeta().getLore();
        } else {
            lores = new ArrayList<String>();
        }
        if (autoPricePrefix) { lore = "[AP] "+lore; }
        lores.add(lore);
        meta.setLore(lores);
        stack.setItemMeta(meta);
    }

    void removeAutoPriceLores(ItemStack stack) {
        if (stack == null) { return; }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) { return; }
        
        if (meta.hasLore()) {
            List<String> loresOriginal = stack.getItemMeta().getLore();
            List<String> loresNew = new ArrayList<String>();
            for (String lore : loresOriginal) {
                if (!lore.startsWith("[AP]")) {
                    loresNew.add(lore);
                }
            }
            if (loresOriginal != loresNew) {
                meta.setLore(loresNew);
                stack.setItemMeta(meta);
            }
        }
    }    
    
    public void addPurchaseInfo(ItemStack stack, String shopName, String playerName) {
        //Add custom material name as a lore?
        if (plugin.getConfig().getBoolean(plugin.configuration.getStackConfigPath(stack,shopName)+".displayCustomMaterialNameInShop",
                plugin.getConfig().getBoolean("displayCustomMaterialNameInShop",false))) {
            
            addLore(stack, String.format(plugin.getData.getPlayerMessage("name", playerName), plugin.getData.getInternalMaterialName(stack,shopName)),true);
        }
        //Display price or "out of stock"
        if (plugin.getData.getTotalStockAmount(stack, shopName) >= 1) {
            addLore(stack, String.format(plugin.getData.getPlayerMessage("purchasePrice", playerName), 
                    plugin.formatPrice(plugin.prices.getMinimumPurchasePrice(stack, shopName),'.',' ',true,true,false)  ),true);
            addLore(stack, String.format(plugin.getData.getPlayerMessage("clickBuy", playerName), "1","4","16","64"),true);
        } else {
            addLore(stack, plugin.getData.getPlayerMessage("noStock", playerName),true);
        }
        //Basic instructions
        addLore(stack, plugin.getData.getPlayerMessage("buttons", playerName),true);
        addLore(stack, "("+String.format(plugin.getData.getPlayerMessage("salesPrice", playerName), 
                plugin.formatPrice(plugin.prices.getFinalPrice(false, stack, shopName),'.',' ',true,true,false)+")"
        ),true);
    } 
    
    public void addSalesInfo(ItemStack stack, String shopName, String playerName) {
        //Skip if not tradable
        if (!plugin.getData.isTradable(stack,shopName)) { return; }
        
        //Add custom material name as a lore?
        if (plugin.getConfig().getBoolean(plugin.configuration.getStackConfigPath(stack,shopName)+".displayCustomMaterialNameInShop",
                plugin.getConfig().getBoolean("displayCustomMaterialNameInShop",false))) {
            
            addLore(stack,"Name: "+plugin.getData.getInternalMaterialName(stack,shopName),true);
        }

        //Price & basic instructions
        addLore(stack, String.format(plugin.getData.getPlayerMessage("salesPrice", playerName), 
                plugin.formatPrice(plugin.prices.getFinalPrice(false, stack, shopName),'.',' ',true,true,false)  ),true);
        addLore(stack, String.format(plugin.getData.getPlayerMessage("clickSell", playerName), "1","4","16","64"),true);
        addLore(stack, plugin.getData.getPlayerMessage("buttons", playerName),true);
        
        //Display purchase price or "out of stock"
        if (plugin.getData.getTotalStockAmount(stack, shopName) >= 1) {
            addLore(stack, "("+String.format(plugin.getData.getPlayerMessage("purchasePrice", playerName), 
                    plugin.formatPrice(plugin.prices.getMinimumPurchasePrice(stack, shopName),'.',' ',true,true,false)+")")
            ,true);
        } else {
            addLore(stack, plugin.getData.getPlayerMessage("noStock", playerName),true);
        }
    }
    
    public void setShopInfoOnStacks(Inventory inventory, boolean remove, boolean add, String shopName, CommandSender sender) {
        if (inventory == null) { return; }
        //Loop shop inventory
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null) {
                if (remove) {
                    removeAutoPriceLores(stack);     
                }
                //Add purchase info unless it's the options item
                if (add && stack.getType() != Material.LAVA && stack.getType() != Material.WATER) {  
                    addPurchaseInfo(stack, shopName, sender.getName());         
                }
            }
        }

        //Loop player inventory
        if (inventory.getHolder() == null) { return; }
        if (inventory.getHolder().getInventory() == null) { return; }
        for (ItemStack stack : inventory.getHolder().getInventory().getContents()) {
            if (stack != null) {
                if (remove) {
                    removeAutoPriceLores(stack);     
                }
                if (add) {
                    addSalesInfo(stack, shopName, sender.getName());
                }
            }
        }
    }

    public void processPlayerSales(InventoryClickEvent event, String shopName) {
//        What is being sold and how much?
        ItemStack stackToSell    = event.getCurrentItem();
//        Selling more than you have there?
        int amountBeingSold = plugin.getData.getAmountToMove( event.getClick() );
        if (amountBeingSold > stackToSell.getAmount()) { amountBeingSold = stackToSell.getAmount(); }
//        Limit amount by how much can be sold
        String materialConfigPath     = plugin.configuration.getStackConfigPath(stackToSell, shopName);
        int amountPlayerCanSell     
            = plugin.getData.getMaximumAmountPlayerCanSell((CommandSender) event.getWhoClicked(),materialConfigPath,stackToSell,shopName);
        if (amountBeingSold > amountPlayerCanSell) { amountBeingSold = amountPlayerCanSell; }
        if (amountBeingSold < 1) { return; }    //Can't Sell any
//        Pay the price to player
        float salesUnitPrice = plugin.prices.getFinalPrice(false,stackToSell,shopName);    //Get the final unit price for this specific Stack
        EconomyResponse depositOutcome = AutoPrice.economy.depositPlayer((OfflinePlayer) event.getWhoClicked() , amountBeingSold*salesUnitPrice);
        if (depositOutcome.transactionSuccess()) {
            finishPlayerSales(event, amountBeingSold, stackToSell, salesUnitPrice, shopName);
        }
    }
    
    public void finishPlayerSales(InventoryClickEvent event, int amountBeingSold, ItemStack stackToSell, float salesUnitPrice, String shopName) {
        //Notification to player
        plugin.respondToSender((CommandSender) event.getWhoClicked(), 
                String.format(plugin.getData.getConsoleMessage("youSold"), 
                        amountBeingSold, 
                        plugin.getData.getInternalMaterialName(stackToSell, shopName), 
                        plugin.formatPrice(salesUnitPrice,'.',' ',true,true,false), 
                        plugin.formatPrice(amountBeingSold*salesUnitPrice,'.',' ',true,true,false)
                )
        );
        //Notification to console?
        if (plugin.getConfig().getBoolean("postTradesToConsole",false)) {   //Trade messages should be posted to console
            plugin.logger.info(plugin.getData.getMessagePrefix(false)+
                    String.format(plugin.getData.getConsoleMessage("playerSold"), 
                            event.getWhoClicked().getName(), 
                            amountBeingSold, 
                            plugin.getData.getInternalMaterialName(stackToSell, shopName), 
                            plugin.formatPrice(salesUnitPrice,'.',' ',true,true,false), 
                            plugin.formatPrice(amountBeingSold*salesUnitPrice,'.',' ',true,true,false), 
                            shopName
                    )
            );
        }
        //Update item info
        plugin.updateItem(false, amountBeingSold, stackToSell, salesUnitPrice, shopName);
        //Refresh current shop page
        loadShopPage((CommandSender) event.getWhoClicked(), event.getView().getTopInventory(), 
            plugin.getConfig().getString("temporary.players."+event.getWhoClicked().getName()+".shopCurrentPageNumber","1"), shopName);
        //Remove item
        if (stackToSell.getAmount()-amountBeingSold > 0) {
            stackToSell.setAmount(stackToSell.getAmount()-amountBeingSold);
        } else {
            event.getView().getBottomInventory().removeItem(stackToSell);
        }
    }
    
    public void processPlayerPurchase(InventoryClickEvent event, String shopName) {
        //What is being bought and how much?
        ItemStack stackToBuy    = event.getCurrentItem();
        int amountBeingBought     = plugin.getData.getAmountToMove( event.getClick() );
        //Limit amount by how much can be bought
        int amountPlayerCanBuy  = plugin.getData.getMaximumAmountPlayerCanBuy((CommandSender) event.getWhoClicked()
                ,   plugin.configuration.getStackConfigPath(stackToBuy, shopName),    shopName);
        if (amountBeingBought > amountPlayerCanBuy) { 
            amountBeingBought = amountPlayerCanBuy; 
        }
        if (amountBeingBought < 1) {    //Can't buy any
            return; 
        }   
        //Charge the cost from player
        float purchaseUnitPrice = plugin.prices.getFinalPrice(true,stackToBuy,shopName); //Get the final unit price for this specific Stack
        EconomyResponse withdrawalOutcome = 
                AutoPrice.economy.withdrawPlayer((OfflinePlayer) event.getWhoClicked() , amountBeingBought*purchaseUnitPrice);
        if (withdrawalOutcome.transactionSuccess()) {
            finishPlayerPurchase(event, amountBeingBought, stackToBuy, purchaseUnitPrice, shopName);
        }
    }
    
    public void finishPlayerPurchase(InventoryClickEvent event, int amountBeingBought, ItemStack stackToBuy, float purchaseUnitPrice, String shopName) {
        //Notification to player
        plugin.respondToSender((CommandSender) event.getWhoClicked(), 
                String.format(plugin.getData.getConsoleMessage("youBought"), 
                        amountBeingBought, 
                        plugin.getData.getInternalMaterialName(stackToBuy, shopName), 
                        plugin.formatPrice(purchaseUnitPrice,'.',' ',true,true,false), 
                        plugin.formatPrice(amountBeingBought*purchaseUnitPrice,'.',' ',true,true,false)
                )
        );
        //Notification to console?
        if (plugin.getConfig().getBoolean("postTradesToConsole",false)) {   //Trade messages should be posted to console
            plugin.logger.info(plugin.getData.getMessagePrefix(false)+
                String.format(plugin.getData.getConsoleMessage("playerBought"), 
                    event.getWhoClicked().getName(), 
                    amountBeingBought, 
                    plugin.getData.getInternalMaterialName(stackToBuy, shopName), 
                    plugin.formatPrice(purchaseUnitPrice,'.',' ',true,true,false), 
                    plugin.formatPrice(amountBeingBought*purchaseUnitPrice,'.',' ',true,true,false), 
                    shopName
                )
            );

        }
        //Update item info
        plugin.updateItem(true, amountBeingBought, stackToBuy, purchaseUnitPrice, shopName);
        //Refresh current shop page
        loadShopPage((CommandSender) event.getWhoClicked(), event.getView().getTopInventory(), 
            plugin.getConfig().getString("temporary.players."+event.getWhoClicked().getName()+".shopCurrentPageNumber","1"), shopName);
        //Give the stack to the player
        stackToBuy.setAmount(amountBeingBought);
        event.getView().getBottomInventory().addItem(stackToBuy);
    }
}