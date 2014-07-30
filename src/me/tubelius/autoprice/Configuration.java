package me.tubelius.autoprice;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

class Configuration {
    //Pointer to the class calling this class
    private AutoPrice plugin;    
    Configuration(AutoPrice plugin) {
        this.plugin = plugin;
    }

    void upgradeConfig() {
        if (!plugin.getDescription().getVersion().equalsIgnoreCase(plugin.getConfig().getString("configVersion",""))) {
            //Configuration is from earlier version --> upgrade it
            plugin.logger.info(plugin.getData.getMessagePrefix(false)+plugin.getData.getConsoleMessage("upgradingConfigFrom")+" "
                    +plugin.getConfig().getString("configVersion",""));
            plugin.getConfig().set("shopLastPageNumber",null);
            plugin.getConfig().set("defaultStockAmountMinObjective",null);
            plugin.getConfig().set("players",null);        //There used to be temporary data here in old version
            moveConfigNode("materials","shops.default.materials",false,true);               //move
            moveConfigNodesOnAllLevels("priceMin","salesPriceMin",false,false);             //copy
            moveConfigNodesOnAllLevels("priceMin","purchasePriceMin",false,true);           //move
            moveConfigNodesOnAllLevels("priceMax","salesPriceMax",false,false);             //copy
            moveConfigNodesOnAllLevels("priceMax","purchasePriceMax",false,true);           //move
            moveConfigNodesOnAllLevels("salesPriceMin","salesPrice.minPrice",false,true);   //move
            moveConfigNodesOnAllLevels("salesPriceMax","salesPrice.maxPrice",false,true);   //move
            upgradeSubMaterialConfig();
            plugin.getConfig().set("temporary",null);
            moveConfigNode("ticksBetweenPriceUpdates","salesPrice.updateIntervalTicks",false,true);                         //move
            moveConfigNode("initialBaseSalesPrice","salesPrice.price",false,true);                                          //move
            moveConfigNode("minimumPlayersRequiredForPriceChange","salesPrice.playersRequiredForPriceChange",false,true);   //move
            moveConfigNode("priceChecksToSkipAfterTrades","salesPrice.priceChecksToSkipAfterTrades",false,true);            //move
            moveConfigNodesOnAllLevels("baseSalesPriceForPlayer","salesPrice.price",false,true);                            //move
            plugin.getConfig().set("configVersion",plugin.getDescription().getVersion()); //Update configuration version
            plugin.logger.info(plugin.getData.getMessagePrefix(false)+plugin.getData.getConsoleMessage("upgradedConfig")+" "+plugin.getConfig().getString("configVersion",""));
            plugin.saveConfig(); //Save the configuration file
        }
    }
    
    //Configuration node relocation/copying
    void moveConfigNode(String pathOld, String pathNew, boolean overwrite, boolean deleteOld) {
        if (pathOld == null) {
            return; //old location missing --> exit
        }
        if (pathNew != null) {
            //new path missing, don't create anything
            if (overwrite || plugin.getConfig().get(pathNew,null) == null) {
                //overwriting is allowed of new node is missing --> save
                plugin.getConfig().set(pathNew,plugin.getConfig().get(pathOld));
            }
        }
        if (deleteOld) { plugin.getConfig().set(pathOld, null); }  //Delete the old node?
    }
    private void moveConfigNodesInAllMaterials(String pathOldRelative, String pathNewRelative, boolean overwrite, boolean deleteOld) {
        for (String shopName : plugin.getConfig().getConfigurationSection("shops").getKeys(false)) {
            for (String materialNode : plugin.getConfig().getConfigurationSection("shops."+shopName+".materials").getKeys(false)) {
                String materialPath = "shops."+shopName+".materials."+materialNode;
                moveConfigNode(materialPath+"."+pathOldRelative , materialPath+"."+pathNewRelative , overwrite , deleteOld);
            }
        }
    }
    private void moveConfigNodesInAllShops(String pathOldRelative, String pathNewRelative, boolean overwrite, boolean deleteOld) {
        for (String shopName : plugin.getConfig().getConfigurationSection("shops").getKeys(false)) {
            String shopNode = "shops."+shopName;
            moveConfigNode(shopNode+"."+pathOldRelative , shopNode+"."+pathNewRelative , overwrite , deleteOld);
        }
    }
    private void moveConfigNodesOnAllLevels(String pathOldRelative, String pathNewRelative, boolean overwrite, boolean deleteOld) {
        moveConfigNodesInAllMaterials(pathOldRelative, pathNewRelative, overwrite, deleteOld);
        moveConfigNodesInAllShops(pathOldRelative, pathNewRelative, overwrite, deleteOld);
        moveConfigNode(pathOldRelative, pathNewRelative, overwrite, deleteOld);
    }
    
    private void upgradeSubMaterialConfig() {
        for (String shopName : plugin.getConfig().getConfigurationSection("shops").getKeys(false)) {
            //relocate material nodes
            for (String materialNode : plugin.getConfig().getConfigurationSection("shops."+shopName+".materials").getKeys(false)) {
                for (String subMaterial : plugin.getConfig().getConfigurationSection("shops."+shopName+".materials."+materialNode).getKeys(false)) {
                    if (NumberUtils.isNumber(subMaterial)) {
                        String oldMaterialPath = "shops."+shopName+".materials."+materialNode+"."+subMaterial;
                        String newMaterialName = plugin.getConfig().getString(oldMaterialPath+".name",materialNode);
                        String newMaterialPath = "shops."+shopName+".materials."+newMaterialName;
                        int materialSuffix = 2;
                        while (plugin.getConfig().isString(newMaterialPath+".mainMaterial")) {
                            newMaterialPath = "shops."+shopName+".materials."+newMaterialName+materialSuffix; 
                            materialSuffix += 1;
                        }
                        plugin.getConfig().set(newMaterialPath , plugin.getConfig().getConfigurationSection(oldMaterialPath));  //Copy to new location
                        plugin.getConfig().set(oldMaterialPath , null);                                                         //Remove old location
                        plugin.getConfig().set(newMaterialPath+".name" , null);                                 //Remove name node (it's in path now)
                        plugin.getConfig().set(newMaterialPath+".mainMaterial" , materialNode);                 //Add node
                        plugin.getConfig().set(newMaterialPath+".subMaterial" , Integer.parseInt(subMaterial)); //Add node
                    }
                }
            }
            //remove leftover material nodes
            for (String materialNode : plugin.getConfig().getConfigurationSection("shops."+shopName+".materials").getKeys(false)) {
                if (!plugin.getConfig().isString("shops."+shopName+".materials."+materialNode+".mainMaterial")) {
                    plugin.getConfig().set("shops."+shopName+".materials."+materialNode , null);   //Remove left over node
                }
            }
            //remove unsupported keys under materials
            for (String materialNode : plugin.getConfig().getConfigurationSection("shops."+shopName+".materials").getKeys(false)) {
                plugin.getConfig().set("shops."+shopName+".materials."+materialNode+".stockAmountMinObjective",null); //No longer supported
            }
        }
    }
    

    String createMaterialConfiguration(ItemStack stackInHand, String shopName) {
        String internalMaterialName = ChatColor.stripColor(stackInHand.getItemMeta().getDisplayName() );  //stackInHand.getType().name();
        if (internalMaterialName == null) { internalMaterialName = stackInHand.getType().name(); }
        String newMaterialPath = "shops."+shopName+".materials."+internalMaterialName;
        int materialSuffix = 2;
        while (plugin.getConfig().isString(newMaterialPath+".mainMaterial") ) {
            internalMaterialName += materialSuffix;
            newMaterialPath = "shops."+shopName+".materials."+internalMaterialName; 
            materialSuffix += 1;
        }
        //main material
        plugin.getConfig().set(newMaterialPath+".mainMaterial" , stackInHand.getType().name() );
        //sub material (if has)
        if (stackInHand.getType().getMaxDurability() == 0) {  //Material has sub materials instead of durability
            plugin.getConfig().set(newMaterialPath+".subMaterial" , stackInHand.getDurability() );
        }
        //item meta
        ItemMeta meta = stackInHand.getItemMeta();
        if (meta != null) { 
            saveItemStackMetaToConfig(newMaterialPath,meta,stackInHand);
        }            
        //enchantments
        Map<Enchantment,Integer> enchantments = stackInHand.getEnchantments();
        Iterator<Entry<Enchantment,Integer>> iter = enchantments.entrySet().iterator();
        while(iter.hasNext()){
            Entry<Enchantment,Integer> entry = iter.next();
            plugin.getConfig().set(newMaterialPath+".enchantments."+entry.getKey().getName(), entry.getValue() );
        }
        //tradability
        plugin.getConfig().set(newMaterialPath+".tradable" , true);
        return  internalMaterialName;   //return the internal name of the material created
    }

    private void saveItemStackMetaToConfig(String newMaterialPath, ItemMeta meta, ItemStack stackInHand) {
        //display name
        plugin.getConfig().set(newMaterialPath+".displayName", meta.getDisplayName() );
        //lores
        if (meta.hasLore() ) {
            List<String> lores = stackInHand.getItemMeta().getLore();
            ArrayList<String> loresForConfig = new ArrayList<String>();
            for (String lore : lores) {
                if ( !lore.startsWith(plugin.getData.getPrefix()) ) {
                    loresForConfig.add(lore);
                }
            }
            plugin.getConfig().set(newMaterialPath+".lores", loresForConfig);
        }
        if (meta instanceof LeatherArmorMeta) {
            //LeatherArmorMeta color
            LeatherArmorMeta leatherArmorMeta = (LeatherArmorMeta)stackInHand.getItemMeta();
            plugin.getConfig().set(newMaterialPath+".color" , leatherArmorMeta.getColor() );
            stackInHand.setItemMeta(leatherArmorMeta);
        }
    }
    
    String getStackConfigPath(ItemStack stack, String shopName) {
        //Get the configuration path where the data is stored for this Material or MaterialData
        //loop materials in configuration -> return matching material's path
        for (String materialNode : plugin.getConfig().getConfigurationSection("shops."+shopName+".materials").getKeys(false)) {
            String materialPath = "shops."+shopName+".materials."+materialNode;
            if ( stack.getType().name().equalsIgnoreCase(plugin.getConfig().getString(materialPath+".mainMaterial"))) {
                //main material matches -> item uses durability or sub material in durability value matches configuration 
                if (stack.getType().getMaxDurability() > 0 || 
                        stack.getDurability() == plugin.getConfig().getInt(materialPath+".subMaterial")) {
                    //main&sub materials match -> check/match lores if requested
                    if (plugin.getData.loresMatch(materialPath,stack) || !getMaterialConfigBoolean(shopName, materialPath, "matchLores")) {
                        return  materialPath;   //current material matches the stack, return the path of current material
                    }
                }
            }
        }
        return  null;
    }
    
    double getMaterialConfigDouble(String shopName, String materialPath, String node) {
        return 
            plugin.getConfig().getDouble(materialPath+"."+node          //Primarily use material config
            ,   plugin.getConfig().getDouble("shops."+shopName+"."+node //If missing use shop config
                ,   plugin.getConfig().getDouble(node)                  //If missing use root (global default)
                )
            );
    }
    int getMaterialConfigInt(String shopName, String materialPath, String node) {
        return 
            plugin.getConfig().getInt(materialPath+"."+node             //Primarily use material config
            ,   plugin.getConfig().getInt("shops."+shopName+"."+node    //If missing use shop config
                ,   plugin.getConfig().getInt(node)                     //If missing use root (global default)
                )
            );
    }
    private boolean getMaterialConfigBoolean(String shopName, String materialPath, String node) {
        return 
            plugin.getConfig().getBoolean(materialPath+"."+node          //Primarily use material config
            ,   plugin.getConfig().getBoolean("shops."+shopName+"."+node //If missing use shop config
                ,   plugin.getConfig().getBoolean(node)                  //If missing use root (global default)
                )
            );
    }
}