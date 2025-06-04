import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;
import java.text.DateFormat;
import java.util.Date;
import java.nio.charset.StandardCharsets;

public class SyncBundles{
  private static final Pattern matcher = Pattern.compile("\\\\\\n");
  public static final char SEP = '\\';

  public static void main(String[] args){
    Properties info = new Properties(new File("bundleInfo.properties"));
    String sourceLocale = info.get("source");
    String bundlesDir = info.get("bundlesDir").replace("/", File.separator);
    String modName = info.get("modName");
    Properties loc = new Properties();
    loc.read(info.getOrigin("targetLocales").replace("[", "").replace("]", "").replace(SEP + System.lineSeparator(), System.lineSeparator()));
    String[] locales = new String[loc.map.size()*2];
    int index = 0;
    for(Pair entry: loc.map.values()){
      locales[index] = entry.key.replace("en_US", "");
      locales[index+1] = entry.value;
      index += 2;
    }

    File source = new File(bundlesDir, "bundle" + (sourceLocale.isBlank() ? "": "_" + sourceLocale) + ".properties");
    Properties sourceBundle = new Properties();
    sourceBundle.read(source);

    handleHeader(sourceBundle, modName, sourceLocale, args);

    sourceBundle.write(source);
    for(int i = 0; i < locales.length; i += 2){
      String locale = locales[i], mark = locales[i + 1];
      File file = new File(bundlesDir, "bundle" + (locale.isBlank() ? "": "_" + locale) + ".properties");
      Properties bundle = new Properties(sourceBundle, mark);
      if(file.exists()) bundle.read(file);
      handleHeader(bundle, modName, locale, args);
      bundle.write(file);
    }
  }

  public static void handleHeader(Properties source, String name, String locTag, String... args){
    source.put(name + ".mod.updateDate", DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.forLanguageTag(locTag.replace("_", "-"))).format(new Date()), 0);

    source.put(name + ".mod.version", args[0], 0);
  }

  public static class Properties{
    ArrayList<Line> lines = new ArrayList<>();
    LinkedHashMap<String, Pair> map = new LinkedHashMap<>();

    String mark;

    public Properties(){}

    public Properties(File file){
      read(file);
    }

    public Properties(Properties source, String mark){
      this.mark = mark;
      for(Line line: source.lines){
        Line l;
        if(line instanceof Pair){
          l = new Pair(line.string, ((Pair) line).key, mark + " " + ((Pair) line).value);
          map.put(((Pair) l).key, (Pair) l);
        }
        else l = new Line(line.string);
        lines.add(l);
      }
    }

    public void put(String lineStr, int line){
      lines.add(line, new Line(lineStr));
    }

    public void put(String key, String value, int line){
      key = string2Unicode(key);
      String v = string2Unicode(value);

      Pair pair = map.computeIfAbsent(key, k -> {
        Pair r = new Pair(k + " = " + v, k, v);
        lines.add(line, r);

        return r;
      });
      pair.value = v;
    }

    public String get(String key){
      String str = map.containsKey(key)? map.get(key).value: null;
      StringBuilder result = new StringBuilder();

      for(String res: matcher.split(str)){
        result.append(res);
      }

      return result.toString();
    }

    public String getOrigin(String key){
      return map.containsKey(key)? map.get(key).value: null;
    }

    public void read(String content){
      read(new BufferedReader(new StringReader(content)));
    }

    public void read(File file){
      try{
        read(new BufferedReader(new FileReader(file)));
      }catch(FileNotFoundException e){
        e.printStackTrace();
      }
    }

    public void read(BufferedReader reader){
      try{
        String line;
        boolean init = lines.isEmpty();
        Pair last = null;

        while((line = reader.readLine()) != null){
          if(last != null){
            last.value = last.value + line;

            if(line.endsWith(String.valueOf(SEP))){
              last.value += System.lineSeparator();
            }
            else last = null;

            continue;
          }

          String[] strs = line.trim().split("=", 2);
          if(init){
            if(!strs[0].startsWith("#") && strs.length == 2){
              Pair p;
              lines.add(p = new Pair(line, strs[0].trim(), strs[1].trim()));
              map.put(p.key, p);
              if(strs[1].endsWith(String.valueOf(SEP))){
                last = p;
                p.value = p.value + System.lineSeparator();
              }
            }
            else{
              lines.add(new Line(line));
            }
          }
          else {
            if(strs.length != 2)
              continue;
            Pair pair = map.get(strs[0].trim());
            if(pair != null && !strs[1].trim().startsWith(mark)){
              pair.value = strs[1].trim();
              if(strs[1].endsWith(String.valueOf(SEP))){
                last = pair;
                pair.value += System.lineSeparator();
              }
            }
          }
        }
      }catch(IOException e){
        throw new RuntimeException(e);
      }
    }

    public void write(File file) {
      try {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.ISO_8859_1, false));
        for (Line line: lines) {
          writer.write(line.toString());
          writer.newLine();
          writer.flush();
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static class Line{
    String string;

    public Line(String line){
      this.string = line;
    }

    @Override
    public String toString(){
      return string;
    }
  }

  public static class Pair extends Line{
    String key;
    String value;

    public Pair(String line, String key, String value){
      super(line);
      this.key = key;
      this.value = value;
    }

    @Override
    public String toString(){
      return key + " = " + value;
    }
  }

  private static String string2Unicode(String string) {
    StringBuilder unicode = new StringBuilder();
    for (int i = 0; i < string.length(); i++) {
      // 取出每一个字符
      char c = string.charAt(i);
      if (c<0x20 || c>0x7E) {
        // 转换为unicode
        String tmp = Integer.toHexString(c).toUpperCase();
        if (tmp.length() == 4) {
          unicode.append("\\u").append(tmp);
        } else if (tmp.length() == 3){
          unicode.append("\\u0").append(tmp);
        } else if (tmp.length() == 2){
          unicode.append("\\u00").append(tmp);
        } else {
          unicode.append("\\u000").append(tmp);
        }
      } else {
        unicode.append(c);
      }
    }
    return unicode.toString();
  }
}
