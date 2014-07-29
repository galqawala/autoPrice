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

public class Configuration {
    //Pointer to the class calling this class
    private AutoPrice Plugin;    
    public Configuration(AutoPrice Plugin) {
        this.Plugin = Plugin;
    }

    void upgradeConfig() {
        if (!Plugin.getDescription().getVersion().equalsIgnoreCase(Plugin.getConfig().getString("configVersion",""))) {
            //Configuration is from earlier version --> upgrade it
            Plugin.logger.info(Plugin.GetData.getMessagePrefix(false)+Plugin.GetData.getConsoleMessage("upgradingConfigFrom")+" "
                    +Plugin.getConfig().getString("configVersion",""));
            Plugin.getConfig().set("shopLastPageNumber",null);
            Plugin.getConfig().set("defaultStockAmountMinObjective",null);
            Plugin.getConfig().set("players",null);        //There used to be temporary data here in old version
            moveConfigNode("materials","shops.default.materials",false,true);               //move
            moveConfigNodesOnAllLevels("priceMin","salesPriceMin",false,false);             //copy
            moveConfigNodesOnAllLevels("priceMin","purchasePriceMin",false,true);           //move
            moveConfigNodesOnAllLevels("priceMax","salesPriceMax",false,false);             //copy
            moveConfigNodesOnAllLevels("priceMax","purchasePriceMax",false,true);           //move
            moveConfigNodesOnAllLevels("salesPriceMin","salesPrice.minPrice",false,true);   //move
            moveConfigNodesOnAllLevels("salesPriceMax","salesPrice.maxPrice",false,true);   //move
            upgradeSubMaterialConfig();
            Plugin.getConfig().set("temporary",null);
            moveConfigNode("ticksBetweenPriceUpdates","salesPrice.updateIntervalTicks",false,true);                         //move
            moveConfigNode("initialBaseSalesPrice","salesPrice.price",false,true);                                          //move
            moveConfigNode("minimumPlayersRequiredForPriceChange","salesPrice.playersRequiredForPriceChange",false,true);   //move
            moveConfigNode("priceChecksToSkipAfterTrades","salesPrice.priceChecksToSkipAfterTrades",false,true);            //move
            moveConfigNodesOnAllLevels("baseSalesPriceForPlayer","salesPrice.price",false,true);                            //move
            Plugin.getConfig().set("configVersion",Plugin.getDescription().getVersion()); //Update configuration version
            Plugin.logger.info(Plugin.GetData.getMessagePrefix(false)+Plugin.GetData.getConsoleMessage("upgradedConfig")+" "+Plugin.getConfig().getString("configVersion",""));
            Plugin.saveConfig(); //Save the configuration file
        }
    }

    
    //Configuration node relocation/copying
    public void moveConfigNode(String pathOld, String pathNew, boolean overwrite, boolean deleteOld) {
        if (pathOld == null) {
            return; //old location missing --> exit
        }
        if (pathNew != null) {
            //new path missing, don't create anything
            if (overwrite || Plugin.getConfig().get(pathNew,null) == null) {
                //overwriting is allowed of new node is missing --> save
                Plugin.getConfig().set(pathNew,Plugin.getConfig().get(pathOld));
            }
        }
        if (deleteOld) { Plugin.getConfig().set(pathOld, null); }  //Delete the old node?
    }
    public void moveConfigNodesInAllMaterials(String pathOldRelative, String pathNewRelative, boolean overwrite, boolean deleteOld) {
        for (String shopName : Plugin.getConfig().getConfigurationSection("shops").getKeys(false)) {
            for (String materialNode : Plugin.getConfig().getConfigurationSection("shops."+shopName+".materials").getKeys(false)) {
                String materialPath = "shops."+shopName+".materials."+materialNode;
                moveConfigNode(materialPath+"."+pathOldRelative , materialPath+"."+pathNewRelative , overwrite , deleteOld);
            }
        }
    }
    public void moveConfigNodesInAllShops(String pathOldRelative, String pathNewRelative, boolean overwrite, boolean deleteOld) {
        for (String shopName : Plugin.getConfig().getConfigurationSection("shops").getKeys(false)) {
            String shopNode = "shops."+shopName;
            moveConfigNode(shopNode+"."+pathOldRelative , shopNode+"."+pathNewRelative , overwrite , deleteOld);
        }
    }
    public void moveConfigNodesOnAllLevels(String pathOldRelative, String pathNewRelative, boolean overwrite, boolean deleteOld) {
        moveConfigNodesInAllMaterials(pathOldRelative, pathNewRelative, overwrite, deleteOld);
        moveConfigNodesInAllShops(pathOldRelative, pathNewRelative, overwrite, deleteOld);
        moveConfigNode(pathOldRelative, pathNewRelative, overwrite, deleteOld);
    }
    
    
    private void upgradeSubMaterialConfig() {
        for (String shopName : Plugin.getConfig().getConfigurationSection("shops").getKeys(false)) {
            //relocate material nodes
            for (String materialNode : Plugin.getConfig().getConfigurationSection("shops."+shopName+".materials").getKeys(false)) {
                for (String subMaterial : Plugin.getConfig().getConfigurationSection("shops."+shopName+".materials."+materialNode).getKeys(false)) {
                    if (NumberUtils.isNumber(subMaterial)) {
                        String oldMaterialPath = "shops."+shopName+".materials."+materialNode+"."+subMaterial;
                        String newMaterialName = Plugin.getConfig().getString(oldMaterialPath+".name",materialNode);
                        String newMaterialPath = "shops."+shopName+".materials."+newMaterialName;
                        int materialSuffix = 2;
                        while (Plugin.getConfig().isString(newMaterialPath+".mainMaterial")) {
                            newMaterialPath = "shops."+shopName+".materials."+newMaterialName+materialSuffix; 
                            materialSuffix += 1;
                        }
                        Plugin.getConfig().set(newMaterialPath , Plugin.getConfig().getConfigurationSection(oldMaterialPath));    //Copy to new location
                        Plugin.getConfig().set(oldMaterialPath , null);                                                    //Remove old location
                        Plugin.getConfig().set(newMaterialPath+".name" , null);                                    //Remove name node (it's in path now)
                        Plugin.getConfig().set(newMaterialPath+".mainMaterial" , materialNode);                    //Add node (this info is no longer in path)
                        Plugin.getConfig().set(newMaterialPath+".subMaterial" , Integer.parseInt(subMaterial));    //Add node (this info is no longer in path)
                    }
                }
            }
            //remove leftover material nodes
            for (String materialNode : Plugin.getConfig().getConfigurationSection("shops."+shopName+".materials").getKeys(false)) {
                if (!Plugin.getConfig().isString("shops."+shopName+".materials."+materialNode+".mainMaterial")) {
                    Plugin.getConfig().set("shops."+shopName+".materials."+materialNode , null);   //Remove left over node
                }
            }
            //remove unsupported keys under materials
            for (String materialNode : Plugin.getConfig().getConfigurationSection("shops."+shopName+".materials").getKeys(false)) {
                Plugin.getConfig().set("shops."+shopName+".materials."+materialNode+".stockAmountMinObjective",null); //No longer supported
            }
        }
    }
    

    String createMaterialConfiguration(ItemStack stackInHand, String shopName) {
        String internalMaterialName = ChatColor.stripColor(stackInHand.getItemMeta().getDisplayName() );  //stackInHand.getType().name();
        if (internalMaterialName == null) { internalMaterialName = stackInHand.getType().name(); }
        String newMaterialPath = "shops."+shopName+".materials."+internalMaterialName;
        int materialSuffix = 2;
        while (Plugin.getConfig().isString(newMaterialPath+".mainMaterial") ) {
            internalMaterialName += materialSuffix;
            newMaterialPath = "shops."+shopName+".materials."+internalMaterialName; 
            materialSuffix += 1;
        }
        //main material
        Plugin.getConfig().set(newMaterialPath+".mainMaterial" , stackInHand.getType().name() );
        //sub material (if has)
        if (stackInHand.getType().getMaxDurability() == 0) {  //Material has sub materials instead of durability
            Plugin.getConfig().set(newMaterialPath+".subMaterial" , stackInHand.getDurability() );
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
            Plugin.getConfig().set(newMaterialPath+".enchantments."+entry.getKey().getName(), entry.getValue() );
        }
        //tradability
        Plugin.getConfig().set(newMaterialPath+".tradable" , true);
        return  internalMaterialName;   //return the internal name of the material created
    }

    private void saveItemStackMetaToConfig(String newMaterialPath, ItemMeta meta, ItemStack stackInHand) {
        //display name
        Plugin.getConfig().set(newMaterialPath+".displayName", meta.getDisplayName() );
        //lores
        if (meta.hasLore() ) {
            List<String> lores = stackInHand.getItemMeta().getLore();
            ArrayList<String> loresForConfig = new ArrayList<String>();
            for (String lore : lores) {
                if (!lore.startsWith("[AP]") ) {
                    loresForConfig.add(lore);
                }
            }
            Plugin.getConfig().set(newMaterialPath+".lores", loresForConfig);
        }
        if (meta instanceof LeatherArmorMeta) {
            //LeatherArmorMeta color
            LeatherArmorMeta leatherArmorMeta = (LeatherArmorMeta)stackInHand.getItemMeta();
            Plugin.getConfig().set(newMaterialPath+".color" , leatherArmorMeta.getColor() );
            stackInHand.setItemMeta(leatherArmorMeta);
        }
    }
    
    public String getStackConfigPath(ItemStack stack, String shopName) {
        //Get the configuration path where the data is stored for this Material or MaterialData
        //loop materials in configuration -> return matching material's path
        for (String materialNode : Plugin.getConfig().getConfigurationSection("shops."+shopName+".materials").getKeys(false)) {
            String materialPath = "shops."+shopName+".materials."+materialNode;
            if ( stack.getType().name().equalsIgnoreCase(Plugin.getConfig().getString(materialPath+".mainMaterial"))) {
                //main material matches -> item uses durability or sub material in durability value matches configuration 
                if (stack.getType().getMaxDurability() > 0 || 
                        stack.getDurability() == Plugin.getConfig().getInt(materialPath+".subMaterial")) {
                    //main&sub materials match -> check/match lores
                    if (Plugin.GetData.loresMatch(materialPath,stack)) {
                        return  materialPath;   //current material matches the stack, return the path of current material
                    }
                }
            }
        }
        return  null;
    }
    
    public double getMaterialConfigDouble(String shopName, String materialPath, String node) {
        return 
            Plugin.getConfig().getDouble(materialPath+"."+node          //Primarily use material config
            ,   Plugin.getConfig().getDouble("shops."+shopName+"."+node //If missing use shop config
                ,   Plugin.getConfig().getDouble(node)                  //If missing use root (global default)
                )
            );
    }
    public int getMaterialConfigInt(String shopName, String materialPath, String node) {
        return 
            Plugin.getConfig().getInt(materialPath+"."+node             //Primarily use material config
            ,   Plugin.getConfig().getInt("shops."+shopName+"."+node    //If missing use shop config
                ,   Plugin.getConfig().getInt(node)                     //If missing use root (global default)
                )
            );
    }
}