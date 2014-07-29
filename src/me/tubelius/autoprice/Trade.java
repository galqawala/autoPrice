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
    private AutoPrice Plugin;        
    public Trade(AutoPrice Plugin) { this.Plugin = Plugin; }

    public void addStockForPurchasePrice(ItemStack stack, float purchasePrice, float amountToAdd, String shopName) {
        String configPath = Plugin.Configuration.getStackConfigPath(stack, shopName);
        
        float stockAmount = Plugin.GetData.getStockForPurchasePrice(configPath,purchasePrice);
        stockAmount += amountToAdd;
        
        if (stockAmount > 0) {    //Update stock
            Plugin.getConfig().set(configPath+".stockPerPurchasePrice."+Plugin.formatPrice(purchasePrice,',',' ',false,false,false) , stockAmount);
        } else {    //Remove purchase price
            Plugin.getConfig().set(configPath+".stockPerPurchasePrice."+Plugin.formatPrice(purchasePrice,',',' ',false,false,false) , null);
        }
    }

    public void loadShopPage(CommandSender sender, Inventory shopInventory, String pageNumberToLoad, String shopName) {
        shopInventory.clear();
        String[][] materials = Plugin.GetData.getTradableSubMaterials(sender, shopName);    //updates page count too
        shopInventory.setItem(Plugin.getConfig().getInt("shops."+shopName+".optionsItemLocation" , Plugin.getConfig().getInt("optionsItemLocation"))
                , getShopOptionsItem(sender,pageNumberToLoad)); //requires page count (run getTradableSubMaterials first) 

        for (String[] subMaterial : materials) {
            if (subMaterial[0] != null) {
                //0 = main material, 1 = sub material, 2 = config path, 3 = purchase price, 4 = sales price, 5 = page number

                //Add item if it belongs to page being loaded
                if (subMaterial[5].equalsIgnoreCase(pageNumberToLoad)) {
                    int amount = Plugin.GetData.getMaximumAmountPlayerCanBuy(sender,subMaterial[2],shopName);
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
        Plugin.getConfig().set("temporary.players."+sender.getName()+".shopCurrentPageNumber",Integer.parseInt(pageNumberToLoad));
    }
    
    private ItemStack getShopOptionsItem(CommandSender sender, String pageNumberToLoad) {
        //options item (to-do: left click = next page, right click = previous page, shift+LMB = change sorting, shift+RMB = change category)
        ItemStack stack = new ItemStack(Material.LAVA, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName("[AP] "+Plugin.GetData.getPlayerMessage("options", sender.getName()));
        //static lores/tooltips
        List<String> lores = new ArrayList<String>();
        String lastPage = Plugin.getConfig().getString("temporary.players."+sender.getName()+".shopLastPageNumber", 
                Integer.toString(Integer.MAX_VALUE) );
        lores.add( String.format(Plugin.GetData.getPlayerMessage("optionsPage", sender.getName()),pageNumberToLoad,lastPage) );
        //sorting
        String sortBy = Plugin.getConfig().getString("temporary.players."+sender.getName()+".shopSortOrder","default");
        if (sortBy=="sp")       { sortBy = Plugin.GetData.getPlayerMessage("sortingSP", sender.getName()); }
        else if (sortBy=="pp")  { sortBy = Plugin.GetData.getPlayerMessage("sortingPP", sender.getName()); }
        lores.add( String.format(Plugin.GetData.getPlayerMessage("optionsSorting", sender.getName()),sortBy) );
        //category & update
        String currentCategory = Plugin.getConfig().getString("temporary.players."+sender.getName()+".category","all");
        lores.add( String.format(Plugin.GetData.getPlayerMessage("optionsCategory", sender.getName()),currentCategory) );
        lores.addAll( Plugin.GetData.getPlayerLanguageStringList("optionsLores", sender.getName()) );
        meta.setLore(lores);
        stack.setItemMeta(meta);
        return stack;
    }

    private void setStackMeta(ItemStack stack, String materialPath) {
        ItemMeta meta = stack.getItemMeta();
        //display name
        String displayName = Plugin.getConfig().getString(materialPath+".displayName");
        if (displayName != null) {
            meta.setDisplayName(displayName);
        }
        //lores
        if (Plugin.getConfig().isList(materialPath+".lores")) {
            meta.setLore(Plugin.getConfig().getStringList(materialPath+".lores"));
        }
        //color
        if (Plugin.getConfig().isColor(materialPath+".color")) {
            if (meta instanceof LeatherArmorMeta) {
                //LeatherArmorMeta color
                LeatherArmorMeta leatherArmorMeta = (LeatherArmorMeta)meta;
                leatherArmorMeta.setColor(Plugin.getConfig().getColor(materialPath+".color"));
                meta = leatherArmorMeta;
            }
        }
        //save meta
        stack.setItemMeta(meta);
        //enchants
        if (Plugin.getConfig().isConfigurationSection(materialPath+".enchantments")) {
            for (String enchantmentName : Plugin.getConfig().getConfigurationSection(materialPath+".enchantments").getKeys(false)) {
                stack.addUnsafeEnchantment(Enchantment.getByName(enchantmentName), 
                        Plugin.getConfig().getInt(materialPath+".enchantments."+enchantmentName));
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
        if (Plugin.getConfig().getBoolean(Plugin.Configuration.getStackConfigPath(stack,shopName)+".displayCustomMaterialNameInShop",
                Plugin.getConfig().getBoolean("displayCustomMaterialNameInShop",false))) {
            
            addLore(stack, String.format(Plugin.GetData.getPlayerMessage("name", playerName), Plugin.GetData.getInternalMaterialName(stack,shopName)),true);
        }
        //Display price or "out of stock"
        if (Plugin.GetData.getTotalStockAmount(stack, shopName) >= 1) {
            addLore(stack, String.format(Plugin.GetData.getPlayerMessage("purchasePrice", playerName), 
                    Plugin.formatPrice(Plugin.Prices.getMinimumPurchasePrice(stack, shopName),'.',' ',true,true,false)  ),true);
            addLore(stack, String.format(Plugin.GetData.getPlayerMessage("clickBuy", playerName), "1","4","16","64"),true);
        } else {
            addLore(stack, Plugin.GetData.getPlayerMessage("noStock", playerName),true);
        }
        //Basic instructions
        addLore(stack, Plugin.GetData.getPlayerMessage("buttons", playerName),true);
        addLore(stack, "("+String.format(Plugin.GetData.getPlayerMessage("salesPrice", playerName), 
                Plugin.formatPrice(Plugin.Prices.getFinalPrice(false, stack, shopName),'.',' ',true,true,false)+")"
        ),true);
    } 
    
    public void addSalesInfo(ItemStack stack, String shopName, String playerName) {
        //Skip if not tradable
        if (!Plugin.GetData.isTradable(stack,shopName)) { return; }
        
        //Add custom material name as a lore?
        if (Plugin.getConfig().getBoolean(Plugin.Configuration.getStackConfigPath(stack,shopName)+".displayCustomMaterialNameInShop",
                Plugin.getConfig().getBoolean("displayCustomMaterialNameInShop",false))) {
            
            addLore(stack,"Name: "+Plugin.GetData.getInternalMaterialName(stack,shopName),true);
        }

        //Price & basic instructions
        addLore(stack, String.format(Plugin.GetData.getPlayerMessage("salesPrice", playerName), 
                Plugin.formatPrice(Plugin.Prices.getFinalPrice(false, stack, shopName),'.',' ',true,true,false)  ),true);
        addLore(stack, String.format(Plugin.GetData.getPlayerMessage("clickSell", playerName), "1","4","16","64"),true);
        addLore(stack, Plugin.GetData.getPlayerMessage("buttons", playerName),true);
        
        //Display purchase price or "out of stock"
        if (Plugin.GetData.getTotalStockAmount(stack, shopName) >= 1) {
            addLore(stack, "("+String.format(Plugin.GetData.getPlayerMessage("purchasePrice", playerName), 
                    Plugin.formatPrice(Plugin.Prices.getMinimumPurchasePrice(stack, shopName),'.',' ',true,true,false)+")")
            ,true);
        } else {
            addLore(stack, Plugin.GetData.getPlayerMessage("noStock", playerName),true);
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
                if (add && stack.getType() != Material.LAVA) {  //Add purchase info unless it's the options item
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
        int amountBeingSold = Plugin.GetData.getAmountToMove( event.getClick() );
        if (amountBeingSold > stackToSell.getAmount()) { amountBeingSold = stackToSell.getAmount(); }
//        Limit amount by how much can be sold
        String materialConfigPath     = Plugin.Configuration.getStackConfigPath(stackToSell, shopName);
        int amountPlayerCanSell     
            = Plugin.GetData.getMaximumAmountPlayerCanSell((CommandSender) event.getWhoClicked(),materialConfigPath,stackToSell,shopName);
        if (amountBeingSold > amountPlayerCanSell) { amountBeingSold = amountPlayerCanSell; }
        if (amountBeingSold < 1) { return; }    //Can't Sell any
//        Pay the price to player
        float salesUnitPrice = Plugin.Prices.getFinalPrice(false,stackToSell,shopName);    //Get the final unit price for this specific Stack
        EconomyResponse depositOutcome = AutoPrice.economy.depositPlayer((OfflinePlayer) event.getWhoClicked() , amountBeingSold*salesUnitPrice);
        if (depositOutcome.transactionSuccess()) {
            finishPlayerSales(event, amountBeingSold, stackToSell, salesUnitPrice, shopName);
        }
    }
    
    public void finishPlayerSales(InventoryClickEvent event, int amountBeingSold, ItemStack stackToSell, float salesUnitPrice, String shopName) {
        //Notification to player
        Plugin.respondToSender((CommandSender) event.getWhoClicked(), 
                String.format(Plugin.GetData.getConsoleMessage("youSold"), 
                        amountBeingSold, 
                        Plugin.GetData.getInternalMaterialName(stackToSell, shopName), 
                        Plugin.formatPrice(salesUnitPrice,'.',' ',true,true,false), 
                        Plugin.formatPrice(amountBeingSold*salesUnitPrice,'.',' ',true,true,false)
                )
        );
        //Notification to console?
        if (Plugin.getConfig().getBoolean("postTradesToConsole",false)) {   //Trade messages should be posted to console
            Plugin.logger.info(Plugin.GetData.getMessagePrefix(false)+
                    String.format(Plugin.GetData.getConsoleMessage("playerSold"), 
                            event.getWhoClicked().getName(), 
                            amountBeingSold, 
                            Plugin.GetData.getInternalMaterialName(stackToSell, shopName), 
                            Plugin.formatPrice(salesUnitPrice,'.',' ',true,true,false), 
                            Plugin.formatPrice(amountBeingSold*salesUnitPrice,'.',' ',true,true,false), 
                            shopName
                    )
            );
        }
        //Update item info
        Plugin.updateItem(false, amountBeingSold, stackToSell, salesUnitPrice, shopName);
        //Refresh current shop page
        loadShopPage((CommandSender) event.getWhoClicked(), event.getView().getTopInventory(), 
            Plugin.getConfig().getString("temporary.players."+event.getWhoClicked().getName()+".shopCurrentPageNumber","1"), shopName);
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
        int amountBeingBought     = Plugin.GetData.getAmountToMove( event.getClick() );
        //Limit amount by how much can be bought
        int amountPlayerCanBuy  = Plugin.GetData.getMaximumAmountPlayerCanBuy((CommandSender) event.getWhoClicked()
                ,   Plugin.Configuration.getStackConfigPath(stackToBuy, shopName),    shopName);
        if (amountBeingBought > amountPlayerCanBuy) { 
            amountBeingBought = amountPlayerCanBuy; 
        }
        if (amountBeingBought < 1) {    //Can't buy any
            return; 
        }   
        //Charge the cost from player
        float purchaseUnitPrice = Plugin.Prices.getFinalPrice(true,stackToBuy,shopName); //Get the final unit price for this specific Stack
        EconomyResponse withdrawalOutcome = 
                AutoPrice.economy.withdrawPlayer((OfflinePlayer) event.getWhoClicked() , amountBeingBought*purchaseUnitPrice);
        if (withdrawalOutcome.transactionSuccess()) {
            finishPlayerPurchase(event, amountBeingBought, stackToBuy, purchaseUnitPrice, shopName);
        }
    }
    
    public void finishPlayerPurchase(InventoryClickEvent event, int amountBeingBought, ItemStack stackToBuy, float purchaseUnitPrice, String shopName) {
        //Notification to player
        Plugin.respondToSender((CommandSender) event.getWhoClicked(), 
                String.format(Plugin.GetData.getConsoleMessage("youBought"), 
                        amountBeingBought, 
                        Plugin.GetData.getInternalMaterialName(stackToBuy, shopName), 
                        Plugin.formatPrice(purchaseUnitPrice,'.',' ',true,true,false), 
                        Plugin.formatPrice(amountBeingBought*purchaseUnitPrice,'.',' ',true,true,false)
                )
        );
        //Notification to console?
        if (Plugin.getConfig().getBoolean("postTradesToConsole",false)) {   //Trade messages should be posted to console
            Plugin.logger.info(Plugin.GetData.getMessagePrefix(false)+
                String.format(Plugin.GetData.getConsoleMessage("playerBought"), 
                    event.getWhoClicked().getName(), 
                    amountBeingBought, 
                    Plugin.GetData.getInternalMaterialName(stackToBuy, shopName), 
                    Plugin.formatPrice(purchaseUnitPrice,'.',' ',true,true,false), 
                    Plugin.formatPrice(amountBeingBought*purchaseUnitPrice,'.',' ',true,true,false), 
                    shopName
                )
            );

        }
        //Update item info
        Plugin.updateItem(true, amountBeingBought, stackToBuy, purchaseUnitPrice, shopName);
        //Refresh current shop page
        loadShopPage((CommandSender) event.getWhoClicked(), event.getView().getTopInventory(), 
            Plugin.getConfig().getString("temporary.players."+event.getWhoClicked().getName()+".shopCurrentPageNumber","1"), shopName);
        //Give the stack to the player
        stackToBuy.setAmount(amountBeingBought);
        event.getView().getBottomInventory().addItem(stackToBuy);
    }
}