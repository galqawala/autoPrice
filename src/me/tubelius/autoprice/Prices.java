package me.tubelius.autoprice;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

public class Prices {
    //Pointer to the class calling this class
    private AutoPrice Plugin;    
    public Prices(AutoPrice Plugin) { this.Plugin = Plugin; }

    public float fitSalesPriceToLimits(float price, String shopName, String materialConfigPath) {
        //Get minimum price from: material or shop or global or default
        float minimumAllowedPrice = (float) Plugin.Configuration.getMaterialConfigDouble(shopName, materialConfigPath, "salesPrice.minPrice");
        //Get maximum price from: material or shop or global or default
        float maximumAllowedPrice = (float) Plugin.Configuration.getMaterialConfigDouble(shopName, materialConfigPath, "salesPrice.maxPrice");         
        //Decide which one to return
        if (price < minimumAllowedPrice) {          // New price is lower than the minimum allowed price
            return minimumAllowedPrice;             // Update the base player sales price to the minimum price
        } else if (price > maximumAllowedPrice) {   // New price is higher than the maximum allowed price
            return maximumAllowedPrice;             // Update the base player sales price to the maximum price
        } else {            // Price fits the limits
            return price;   // Return the original price
        }
    }
    
    public float fitPurchasePriceToLimits(float price, String shopName, String materialConfigPath) {
        //Get minimum price from: material or shop or global or default
        float minimumAllowedPrice = (float) Plugin.Configuration.getMaterialConfigDouble(shopName, materialConfigPath, "purchasePriceMin");
        //Get maximum price from: material or shop or global or default
        float maximumAllowedPrice = (float) Plugin.Configuration.getMaterialConfigDouble(shopName, materialConfigPath, "purchasePriceMax");
        //Decide which one to return
        if (price < minimumAllowedPrice) {          // New price is lower than the minimum allowed price
            return minimumAllowedPrice;             // Update the base player Purchase price to the minimum price
        } else if (price > maximumAllowedPrice) {   // New price is higher than the maximum allowed price
            return maximumAllowedPrice;             // Update the base player Purchase price to the maximum price
        } else {            // Price fits the limits
            return price;   // Return the original price
        }
    }

    public void updateSalesPrices() {
        //loop all shops
        for (String shopName : Plugin.getConfig().getConfigurationSection("shops").getKeys(false)) {
            // Loop all materials and make price changes (this will be called from a timer)
            for (String[] subMaterial : Plugin.GetData.getTradableSubMaterials(null, shopName)) {
                //0 = main material, 1 = sub material, 2 = configuration path, 3 = purchase price, 4 = sales price
                
                //Ran out of materials? --> next shop
                if (subMaterial[2] == null) { break; }
                //Enough players for a price change?
                if (Bukkit.getServer().getOnlinePlayers().size() >= 
                        Plugin.Configuration.getMaterialConfigInt(shopName, subMaterial[2], "salesPrice.playersRequiredForPriceChange")) {
                    
                    updateMaterialSalesPrice(subMaterial, shopName);
                } 
            }
        }
        Plugin.saveConfig();
    }

    private void updateMaterialSalesPrice(String[] subMaterial, String shopName) {
        float totalStockAmount = Plugin.GetData.getTotalStockAmount(subMaterial[2]);
        float currentSalesPrice = Float.parseFloat(subMaterial[4].replace(",","."));
        //Suggest a new price
        float newSalesPriceIfIncreased = (float) (currentSalesPrice 
                * Plugin.Configuration.getMaterialConfigDouble(shopName, subMaterial[2], "salesPrice.increaseMultiplier"));
        if ((newSalesPriceIfIncreased-currentSalesPrice) < 
                Plugin.Configuration.getMaterialConfigDouble(shopName, subMaterial[2], "salesPrice.increaseValueMin")) {
            newSalesPriceIfIncreased = (float) (currentSalesPrice
                    +Plugin.Configuration.getMaterialConfigDouble(shopName, subMaterial[2], "salesPrice.increaseValueMin"));
        } else if ((newSalesPriceIfIncreased-currentSalesPrice) > 
                Plugin.Configuration.getMaterialConfigDouble(shopName, subMaterial[2], "salesPrice.increaseValueMax")) {
            newSalesPriceIfIncreased = (float) (currentSalesPrice
                    +Plugin.Configuration.getMaterialConfigDouble(shopName, subMaterial[2], "salesPrice.increaseValueMax"));
        }
        newSalesPriceIfIncreased = fitSalesPriceToLimits(newSalesPriceIfIncreased, shopName, subMaterial[2]);
        //Inspect the price suggestion and decide whether to update or not
        float currentPurchasePrice = Float.parseFloat(subMaterial[3].replace(",","."));
        if ( shouldSalesPriceIncrease(subMaterial[2], totalStockAmount, shopName, newSalesPriceIfIncreased, currentPurchasePrice) ) {
            //Increase the sales price to make players sell more
            Plugin.getConfig().set(subMaterial[2]+".salesPrice.price" , newSalesPriceIfIncreased );
        }
    }

    public boolean shouldSalesPriceIncrease(String configDataPath, float totalStockAmount, 
            String shopName, float newSalesPriceIfIncreased, float currentPurchasePrice) {
        
        // Boolean to return in the end (in addition to returning the boolean we have to update number of price checks to configuration)
        boolean shouldSalesPriceIncrease = false;
        // Limit (price update will be skipped if there has been both sales and purchases within this limit)
        int priceChecksToSkipAfterTrades = 
                Plugin.Configuration.getMaterialConfigInt(shopName, configDataPath, "salesPrice.priceChecksToSkipAfterTrades");
        // Check the number of price checks done since last trades
        int priceChecksSinceLastPlayerSale  = Plugin.getConfig().getInt(configDataPath + ".priceChecksSinceLastPlayerSale"  , Integer.MAX_VALUE);
        int priceChecksSinceLastPurchase    = Plugin.getConfig().getInt(configDataPath + ".priceChecksSinceLastPurchase"    , Integer.MAX_VALUE);
        //The purchase price the items would get if sales price was increased and they were sold
        float purchasePriceForItemsSoldAfterPriceIncrease = getNewPurchasePrice(configDataPath,shopName,newSalesPriceIfIncreased);
        //Raise?
        if (totalStockAmount < 1) { //No stock & no purchase price
            shouldSalesPriceIncrease = true;
        } else if (purchasePriceForItemsSoldAfterPriceIncrease  <   currentPurchasePrice 
                && priceChecksSinceLastPurchase                 >=  priceChecksToSkipAfterTrades
                && priceChecksSinceLastPlayerSale               >=  priceChecksToSkipAfterTrades) {
            //Has not been traded for a while & after the sales price increase the items sold could still be bought for cheaper than now
            shouldSalesPriceIncrease = true;
        }
        // Increase the number of price checks since last trade
        if (priceChecksSinceLastPlayerSale < Integer.MAX_VALUE) { // Add one price check since last sale
            Plugin.getConfig().set(configDataPath + ".priceChecksSinceLastPlayerSale",
                    priceChecksSinceLastPlayerSale + 1);
        }
        if (priceChecksSinceLastPurchase < Integer.MAX_VALUE) { // Add one price check since last purchase
            Plugin.getConfig().set(configDataPath + ".priceChecksSinceLastPurchase",
                    priceChecksSinceLastPurchase + 1);
        }
        return shouldSalesPriceIncrease;
    }
    
    public void decreaseSalesPriceIfNeed(String configDataPath, ItemStack stack, String shopName) {
        //Basic info
        float baseSalesPriceForPlayer   = (float) Plugin.Configuration.getMaterialConfigDouble(shopName, configDataPath, "salesPrice.price");
        float totalMoneyFromPlayers     = (float) Plugin.getConfig().getDouble(configDataPath+".totalMoneyFromPlayers"  ,0);
        float totalMoneyToPlayers       = (float) Plugin.getConfig().getDouble(configDataPath+".totalMoneyToPlayers"    ,0);
        //Change price?
        if (totalMoneyToPlayers > totalMoneyFromPlayers) {    //Material is causing losses to the server
            //Decrease the sales price to prevent server losing money (amount to decrease depends on ratio of money received and spent)
            baseSalesPriceForPlayer = baseSalesPriceForPlayer * totalMoneyFromPlayers / totalMoneyToPlayers;
            Plugin.getConfig().set(configDataPath+".salesPrice.price" , fitSalesPriceToLimits(baseSalesPriceForPlayer, shopName, configDataPath));
        } 
    }
    
    public float getFinalPrice(Boolean getPurchasePrice, ItemStack stack, String shopName) {
        //Calculate the final price for specific item Stack
        if (!Plugin.GetData.isTradable(stack, shopName)) { return 0; }    //Return 0 if stack is not tradable
        if (getPurchasePrice) {    //Get purchase price
            return getMinimumPurchasePrice(stack, shopName);    //Minimum price for current stocks
        } else {    //Get sales price
            float unitPrice = (float) 
                    Plugin.Configuration.getMaterialConfigDouble(shopName, Plugin.Configuration.getStackConfigPath(stack, shopName), "salesPrice.price");
            return unitPrice * Plugin.GetData.getStackConditionMultiplier(stack);    //Multiply the price by the condition of the stack
        }
    }
    
    public float getMinimumPurchasePrice(ItemStack stack, String shopName) {
        return getMinimumPurchasePrice(Plugin.Configuration.getStackConfigPath(stack, shopName));
    }

    public float getMinimumPurchasePrice(String ConfigPath) {
        //Total stock amount for given sub material
        
        float minimumPurchasePrice = Float.MAX_VALUE;
        
        if (Plugin.getConfig().isConfigurationSection(ConfigPath+".stockPerPurchasePrice")) {
            //Has stock for some purchase prices --> loop and find cheapest stock
            for (String purchasePrice : Plugin.getConfig().getConfigurationSection(ConfigPath+".stockPerPurchasePrice").getKeys(false)) {
                if (    ( minimumPurchasePrice==0 || minimumPurchasePrice > Float.parseFloat(purchasePrice.replace(",",".")) ) 
                        && Plugin.getConfig().getDouble(ConfigPath+".stockPerPurchasePrice."+purchasePrice,0) >= 1) {
                    //  (minimum price is missing OR we found cheaper than minimum) AND there's stock for this price --> update minimum price
                    minimumPurchasePrice = Float.parseFloat(purchasePrice.replace(",","."));
                } else if (Float.parseFloat(purchasePrice.replace(",",".")) != 0 && 
                        Plugin.getConfig().getDouble(ConfigPath+".stockPerPurchasePrice."+purchasePrice,0) <= 0) {
                    //Current price is not 0 (dummy path) and there's no stock --> delete price section
                    Plugin.getConfig().set(ConfigPath+".stockPerPurchasePrice."+purchasePrice, null);
                }
            }
        }
        return minimumPurchasePrice;
    }
    
    public float getNewPurchasePrice(String configDataPath, String shopName, float salesPriceTheMaterialIsSoldAt) {
        //Purchase price items that are sold now will get 
        float purchasePrice = (float) (salesPriceTheMaterialIsSoldAt * Plugin.getConfig().getDouble("purchasePriceMultiplier",1.1));
        float minimumProfitPerUnit = (float) Plugin.getConfig().getDouble("minimumProfitPerUnit");    //If missing --> get default
        if (purchasePrice < salesPriceTheMaterialIsSoldAt+minimumProfitPerUnit) {
            purchasePrice = salesPriceTheMaterialIsSoldAt+minimumProfitPerUnit;
        }
        purchasePrice = fitPurchasePriceToLimits(purchasePrice, shopName, configDataPath);
        return roundTo2Decimals(purchasePrice);
    }
    
    public float roundTo2Decimals(float value) {
        //Plugin.logger.info("debug roundTo2Decimals: "+value+" --> "+((float)Math.round(value * 100)) / 100);
        return ((float)Math.round(value * 100)) / 100;
    }
}