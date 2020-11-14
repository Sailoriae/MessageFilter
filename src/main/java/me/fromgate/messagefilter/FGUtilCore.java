/*  
 *  ReActions, Minecraft bukkit plugin
 *  (c)2012-2014, fromgate, fromgate@gmail.com
 *  http://dev.bukkit.org/server-mods/reactions/
 *    
 *  This file is part of ReActions.
 *  
 *  ReActions is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ReActions is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with ReActions.  If not, see <http://www.gnorg/licenses/>.
 * 
 */


package me.fromgate.messagefilter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

@SuppressWarnings("deprecation")
public abstract class FGUtilCore {
    JavaPlugin plg;
    //конфигурация утилит
    private String px = "";
    private String permprefix="fgutilcore.";
    private String language="english";
    private String plgcmd = "<command>";
    // Сообщения+перевод
    YamlConfiguration lng;
    private boolean savelng = false;
    //String lngfile = this.language+".lng";
    protected HashMap<String,String> msg = new HashMap<String,String>(); //массив сообщений
    private char c1 = 'a'; //цвет 1 (по умолчанию для текста)
    private char c2 = '2'; //цвет 2 (по умолчанию для значений)
    protected String msglist ="";
    private boolean colorconsole = false;  // надо будет добавить методы для конфигурации "из вне"
    private Set<String> log_once = new HashSet<String>();
    protected HashMap<String,Cmd> cmds = new HashMap<String,Cmd>();
    protected String cmdlist ="";
    PluginDescriptionFile des;
    private Logger log = Logger.getLogger("Minecraft");
    Random random = new Random ();
    BukkitTask chId;

    //newupdate-checker
    private boolean project_check_version = true;
    private String project_id = ""; //66204 - PlayEffect
    private String project_name = "";
    private String project_current_version = "";
    private String project_last_version = "";
    //private String project_file_url = "";
    private String project_curse_url = "";
    private String version_info_perm = permprefix+"config"; // кого оповещать об обнволениях
    private String project_bukkitdev="";


    public FGUtilCore(JavaPlugin plg, boolean savelng, String lng, String plgcmd, String permissionPrefix){
        this.permprefix = permissionPrefix.endsWith(".") ? permissionPrefix : permissionPrefix+"."; 
        this.plg = plg;
        this.des = plg.getDescription();
        this.language = lng;
        this.InitMsgFile();
        this.initStdMsg();
        this.fillLoadedMessages();
        this.savelng = savelng;
        this.plgcmd = plgcmd;
        this.px = ChatColor.translateAlternateColorCodes('&',"&3["+des.getName()+"]&f ");
    }


    public void initUpdateChecker(String plugin_name, String project_id, String bukkit_dev_name, boolean enable){
        this.project_id = project_id;
        this.project_curse_url = "https://api.curseforge.com/servermods/files?projectIds="+this.project_id;
        this.project_name =plugin_name;
        this.project_current_version = des.getVersion();
        this.project_check_version =enable&&(!this.project_id.isEmpty());
        this.project_bukkitdev = "http://dev.bukkit.org/bukkit-plugins/"+bukkit_dev_name+"/";

        if (this.project_check_version){
            updateMsg ();
            Bukkit.getScheduler().runTaskTimerAsynchronously(plg,new Runnable(){
                @Override
                public void run() {
                    updateMsg ();                }
            }, (40+getRandomInt(20))*1200,60*1200);
        }

    }

    /* Вывод сообщения о выходе новой версии, вызывать из
     * обработчика события PlayerJoinEvent
     */
    public void updateMsg (Player p){
        if (isUpdateRequired()&&p.hasPermission(this.version_info_perm)){
            printMSG(p, "msg_outdated",'e','6',"&6"+project_name+" v"+des.getVersion());
            printMSG(p,"msg_pleasedownload",'e','6',this.project_current_version);
            printMsg(p, "&3"+this.project_bukkitdev);
        }
    }

    /* Вызывается автоматом при старте плагина,
     * пишет сообщение о выходе новой версии в лог-файл
     */
    public void updateMsg (){
        plg.getServer().getScheduler().runTaskAsynchronously(plg, new Runnable(){
            public void run() {
                updateLastVersion();
                if (isUpdateRequired()) {
                    log.info("["+des.getName()+"] "+project_name+" v"+des.getVersion()+" is outdated! Recommended version is v"+project_last_version);
                    log.info("["+des.getName()+"] "+project_bukkitdev);                    
                }
            }
        });
    }

    private void updateLastVersion(){
        if (!this.project_check_version) return;
        URL url = null;
        try {
            url = new URL(this.project_curse_url);
        } catch (Exception e) {
            this.log("Failed to create URL: "+this.project_curse_url);
            return;
        }

        try {
            URLConnection conn = url.openConnection();
            conn.addRequestProperty("X-API-Key", null);
            conn.addRequestProperty("User-Agent", this.project_name+" using FGUtilCore (by fromgate)");
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String response = reader.readLine();
            JSONArray array = (JSONArray) JSONValue.parse(response);
            if (array.size() > 0) {
                JSONObject latest = (JSONObject) array.get(array.size() - 1);
                String plugin_name = (String) latest.get("name");
                this.project_last_version = plugin_name.replace(this.project_name+" v", "").trim();
                //String plugin_jar_url = (String) latest.get("downloadUrl");
                //this.project_file_url = plugin_jar_url.replace("http://servermods.cursecdn.com/", "http://dev.bukkit.org/media/");
            }
        } catch (Exception e) {
            this.log("Failed to check last version");
        }
    }



    private boolean isUpdateRequired(){
        if (!project_check_version) return false;
        if (project_id.isEmpty()) return false;
        if (project_last_version.isEmpty()) return false;
        if (project_current_version.isEmpty()) return false;
        if (project_current_version.equalsIgnoreCase(project_last_version)) return false;
        double current_version = Double.parseDouble(project_current_version.replaceFirst("\\.", "").replace("/", ""));
        double last_version = Double.parseDouble(project_last_version.replaceFirst("\\.", "").replace("/", ""));
        return (last_version>current_version);
    }


    /* 
     * Инициализация стандартных сообщений
     */
    private void initStdMsg(){
        addMSG ("msg_outdated", "%1% is outdated!");
        addMSG ("msg_pleasedownload", "Please download new version (%1%) from ");
        addMSG ("hlp_help", "Help");
        addMSG ("hlp_thishelp", "%1% - this help");
        addMSG ("hlp_execcmd", "%1% - execute command");
        addMSG ("hlp_typecmd", "Type %1% - to get additional help");
        addMSG ("hlp_typecmdpage", "Type %1% - to see another page of this help");
        addMSG ("hlp_commands", "Command list:");
        addMSG ("hlp_cmdparam_command", "command");
        addMSG ("hlp_cmdparam_page", "page");
        addMSG ("hlp_cmdparam_parameter", "parameter");
        addMSG ("cmd_unknown", "Unknown command: %1%");
        addMSG ("cmd_cmdpermerr", "Something wrong (check command, permissions)");
        addMSG ("enabled", "enabled");
        msg.put("enabled", ChatColor.DARK_GREEN+msg.get("enabled"));
        addMSG ("disabled", "disabled");
        msg.put("disabled", ChatColor.RED+msg.get("disabled"));
        addMSG ("lst_title", "String list:");
        addMSG ("lst_footer", "Page: [%1% / %2%]");
        addMSG ("lst_listisempty", "List is empty");
        addMSG ("msg_config", "Configuration");
        addMSG ("cfgmsg_general_check-updates", "Check updates: %1%");
        addMSG ("cfgmsg_general_language", "Language: %1%");
        addMSG ("cfgmsg_general_language-save", "Save translation file: %1%");
    }


    public void setConsoleColored(boolean colorconsole){
        this.colorconsole = colorconsole;
    }

    public boolean isConsoleColored(){
        return this.colorconsole;
    }

    public void addCmd (String cmd, String perm, String desc_id, String desc_key){
        addCmd (cmd, perm,desc_id,desc_key,this.c1, this.c2,false);
    }

    public void addCmd (String cmd, String perm, String desc_id, String desc_key, char color){
        addCmd (cmd, perm,desc_id,desc_key,this.c1, color,false);
    }

    public void addCmd (String cmd, String perm, String desc_id, String desc_key, boolean console){
        addCmd (cmd, perm,desc_id,desc_key,this.c1, this.c2,console);
    }

    public void addCmd (String cmd, String perm, String desc_id, String desc_key, char color,boolean console){
        addCmd (cmd, perm,desc_id,desc_key,this.c1, color,console);
    }

    public void addCmd (String cmd, String perm, String desc_id, String desc_key, char color1, char color2){
        addCmd (cmd, perm,desc_id,desc_key,color1, color2,false);
    }

    public void addCmd (String cmd, String perm, String desc_id, String desc_key, char color1, char color2, boolean console){
        String desc = getMSG(desc_id,desc_key,color1, color2);
        cmds.put(cmd, new Cmd(this.permprefix+perm,desc,console));
        if (cmdlist.isEmpty()) cmdlist = cmd;
        else cmdlist = cmdlist+", "+cmd;
    }

    /* 
     * Проверка пермишенов и наличия команды
     */

    public boolean checkCmdPerm (CommandSender sender, String cmd){
        if (!cmds.containsKey(cmd.toLowerCase())) return false;
        Cmd cm = cmds.get(cmd.toLowerCase());
        if (sender instanceof Player) return (cm.perm.isEmpty()||sender.hasPermission(cm.perm));
        else return cm.console;
    }

    /* Класс, описывающий команду:
     * perm - постфикс пермишена
     * desc - описание команды
     */
    public class Cmd {
        String perm;
        String desc;
        boolean console;
        public Cmd (String perm, String desc){
            this.perm = perm;
            this.desc = desc;
            this.console = false;
        }
        public Cmd (String perm, String desc, boolean console){
            this.perm = perm;
            this.desc = desc;
            this.console = console;
        }
    }

    public class PageList {
        private List<String> ln;
        private int lpp = 15;
        private String title_msgid="lst_title";
        private String footer_msgid="lst_footer";
        private boolean shownum=false;

        public void setLinePerPage (int lpp){
            this.lpp = lpp;
        }

        public PageList(List<String> ln, String title_msgid,String footer_msgid, boolean shownum){
            this.ln = ln;
            if (!title_msgid.isEmpty())	this.title_msgid =title_msgid; 
            if (!footer_msgid.isEmpty()) this.footer_msgid = footer_msgid;
            this.shownum = shownum;
        }

        public void addLine (String str){
            ln.add(str);
        }

        public boolean isEmpty(){
            return ln.isEmpty();
        }

        public void setTitle(String title_msgid){
            this.title_msgid = title_msgid;

        }

        public void setShowNum(boolean shownum){
        }

        public void setFooter(String footer_msgid){
            this.footer_msgid = footer_msgid;
        }

        public void printPage(CommandSender p, int pnum){
            printPage (p, pnum,this.lpp);
        }

        public void printPage(CommandSender  p, int pnum, int linesperpage){
            if (ln.size()>0){

                int maxp = ln.size()/linesperpage;
                if ((ln.size()%linesperpage)>0) maxp++;
                if (pnum>maxp) pnum = maxp;
                int maxl = linesperpage;
                if (pnum == maxp) {
                    maxl = ln.size()%linesperpage;
                    if (maxp==1) maxl = ln.size();
                }
                if (maxl == 0) maxl = linesperpage;
                if (msg.containsKey(title_msgid)) printMsg(p, "&6&l"+getMSGnc(title_msgid));
                else printMsg(p,title_msgid);

                String numpx="";
                for (int i = 0; i<maxl; i++){
                    if (shownum) numpx ="&3"+ Integer.toString(1+i+(pnum-1)*linesperpage)+". ";
                    printMsg(p, numpx+"&a"+ln.get(i+(pnum-1)*linesperpage));
                }
                if (maxp>1)	printMSG(p, this.footer_msgid,'e','6', pnum,maxp);
            } else printMSG (p, "lst_listisempty",'c'); 
        } 

    }

    public void printPage (CommandSender p, List<String> ln, int pnum, String title, String footer, boolean shownum){
        PageList pl = new PageList (ln, title, footer, shownum);
        pl.printPage(p, pnum);		
    }

    public void printPage (CommandSender p, List<String> ln, int pnum, String title, String footer, boolean shownum, int lineperpage){
        PageList pl = new PageList (ln, title, footer, shownum);
        pl.printPage(p, pnum, lineperpage);
    }


    /*
     * Вывод сообщения пользователю 
     */
    public void printMsg(CommandSender p, String msg){
        String message =ChatColor.translateAlternateColorCodes('&', msg);
        if ((!(p instanceof Player))&&(!colorconsole)) message = ChatColor.stripColor(message);
        p.sendMessage(message);
    }


    /*
     * Запись сообщения в лог 
     */
    public void log (String msg){
        log.info(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', px+msg)));
    }


    /*
     * Перевод
     * 
     */

    /*
     *  Инициализация файла с сообщениями
     */
    public void InitMsgFile(){
        try {
            lng = new YamlConfiguration();
            File f = new File (plg.getDataFolder()+File.separator+this.language+".lng");
            if (f.exists()) lng.load(f);
            else {
                InputStream is = plg.getClass().getResourceAsStream("/language/"+this.language+".lng");
                if (is!=null) lng.load(new InputStreamReader(is, "UTF-8"));
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void fillLoadedMessages(){
        if (lng == null) return;
        for (String key : lng.getKeys(true))
            addMSG(key, lng.getString(key));
    }


    /*
     * Добавлене сообщения в список
     * Убираются цвета.
     * Параметры:
     * key - ключ сообщения
     * txt - текст сообщения
     */
    public void addMSG(String key, String txt){
        msg.put(key, ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', lng.getString(key,txt))));
        if (msglist.isEmpty()) msglist=key;
        else msglist=msglist+","+key;
    }


    /*
     * Сохранение сообщений в файл 
     */
    public void SaveMSG(){
        String [] keys = this.msglist.split(",");
        try {
            File f = new File (plg.getDataFolder()+File.separator+this.language+".lng");
            if (!f.exists()) f.createNewFile();
            YamlConfiguration cfg = new YamlConfiguration();
            for (int i = 0; i<keys.length;i++)
                cfg.set(keys[i], msg.get(keys[i]));
            cfg.save(f);
        } catch (Exception e){
            e.printStackTrace();
        }
    } 

    /*
     *  getMSG (String id, [char color1, char color2], Object param1, Object param2, Object param3... )
     */
    public String getMSG (Object... s){
        String str = "&4Unknown message";
        String color1 = "&"+this.c1;
        String color2 = "&"+this.c2;
        if (s.length>0) {
            String id = s[0].toString();
            str = "&4Unknown message ("+id+")";
            if (msg.containsKey(id)){
                int px = 1;
                if ((s.length>1)&&(s[1] instanceof Character )){
                    px = 2;
                    color1 = "&"+(Character) s[1];
                    if ((s.length>2)&&(s[2] instanceof Character )){
                        px = 3;
                        color2 = "&"+(Character) s[2];
                    }
                }
                str = color1+msg.get(id);
                if (px<s.length)
                    for (int i = px; i<s.length; i++){
                        String f = s[i].toString();
                        if (s[i] instanceof Location){
                            Location loc = (Location) s[i];
                            f = loc.getWorld().getName()+"["+loc.getBlockX()+", "+loc.getBlockY()+", "+loc.getBlockZ()+"]";
                        }
                        str = str.replace("%"+Integer.toString(i-px+1)+"%", color2+f+color1);
                    }

            } else if (this.savelng){
                addMSG(id, str);
                SaveMSG();
            }
        }
        return ChatColor.translateAlternateColorCodes('&', str);
    }

    public void printMSG (CommandSender p, Object... s){
        String message = getMSG (s); 
        if ((!(p instanceof Player))&&(!colorconsole)) message = ChatColor.stripColor(message);
        p.sendMessage(message);
    }

    public void PrintHlpList (CommandSender p, int page, int lpp){
        String title = "&6&l"+this.project_name+" v"+des.getVersion()+" &r&6| "+getMSG("hlp_help",'6');
        List<String> hlp = new ArrayList<String>();
        hlp.add(getMSG("hlp_thishelp","/"+plgcmd+" help"));
        hlp.add(getMSG("hlp_execcmd","/"+plgcmd+" <"+getMSG("hlp_cmdparam_command",'2')+"> ["+getMSG("hlp_cmdparam_parameter",'2')+"]"));
        if (p instanceof Player) hlp.add(getMSG("hlp_typecmdpage","/"+plgcmd+" help <"+getMSG("hlp_cmdparam_page",'2')+">"));

        String [] ks = (cmdlist.replace(" ", "")).split(",");
        if (ks.length>0){
            for (String cmd : ks)
                hlp.add(cmds.get(cmd).desc);
        }
        printPage (p, hlp, page, title, "", false,lpp);
    }


    /*
     *  Тоже, что и MSG, но обрезает цвет
     */
    public String getMSGnc(Object... s){
        return ChatColor.stripColor(getMSG (s));
    }


    public int getRandomInt(int maxvalue){
        return random.nextInt(maxvalue);
    }


    /*
     * Проверка формата строкового представления целых чисел 
     */
    public boolean isIntegerSigned (String str){
        return (str.matches("-?[0-9]+[0-9]*"));
    }

    public boolean isIntegerSigned (String... str){
        if (str.length==0) return false;
        for (String s : str)
            if (!s.matches("-?[0-9]+[0-9]*")) return false;
        return true;
    }

    public boolean isInteger (String str){
        return (str.matches("[0-9]+[0-9]*"));
    }

    public boolean isInteger (String... str){
        if (str.length==0) return false;
        for (String s : str)
            if (!s.matches("[0-9]+[0-9]*")) return false;
        return true;
    }


    public boolean isIntegerGZ (String str){
        return (str.matches("[1-9]+[0-9]*"));
    }

    public boolean isIntegerGZ (String... str){
        if (str.length==0) return false;
        for (String s : str)
            if (!s.matches("[1-9]+[0-9]*")) return false;
        return true;
    }

    public Long parseTime(String time){
        int hh = 0; // часы
        int mm = 0; // минуты
        int ss = 0; // секунды
        int tt = 0; // тики
        int ms = 0; // миллисекунды
        if (isInteger(time)){
            ss = Integer.parseInt(time);
        } else if (time.matches("^[0-5][0-9]:[0-5][0-9]$")){
            String [] ln = time.split(":");
            if (isInteger(ln[0])) mm = Integer.parseInt(ln[0]);
            if (isInteger(ln[1])) ss = Integer.parseInt(ln[1]);
        } else if (time.matches("^([0-9]|0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$")){
            String [] ln = time.split(":");
            if (isInteger(ln[0])) hh = Integer.parseInt(ln[0]);
            if (isInteger(ln[1])) mm = Integer.parseInt(ln[1]);
            if (isInteger(ln[2])) ss = Integer.parseInt(ln[2]);
        } else if (time.matches("^\\d+ms")) {
        	ms = Integer.parseInt(time.replace("ms", ""));
        } else if (time.matches("^\\d+h")) {
        	hh = Integer.parseInt(time.replace("h", ""));
        } else if (time.matches("^\\d+m$")) {
        	mm = Integer.parseInt(time.replace("m", ""));
        } else if (time.matches("^\\d+s$")) {
        	ss = Integer.parseInt(time.replace("s", ""));
        } else if (time.matches("^\\d+t$")) {
        	tt = Integer.parseInt(time.replace("t", ""));
        }
        return (hh*3600000L)+(mm*60000L)+(ss*1000L)+(tt*50L)+ms;
    }

    public boolean returnMSG (boolean result, CommandSender p, Object... s){
    	if (p!=null) this.printMSG(p, s);
    	return result;
    }


}


