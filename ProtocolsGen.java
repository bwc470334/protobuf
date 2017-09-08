



import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author biwenchong
 *
 * @date 2017年9月4日 下午4:39:26
 */
public class ProtocolsGen {
	
	private static Map<String, List<String>> filename2Contents = new HashMap<>();
	private static final String patternStr = "//DO NOT EDIT THIS LINE BEGIN[\\s\\S]*?//DO NOT EDIT THIS LINE END";
	private static Pattern pattern = Pattern.compile(patternStr);
	
	private static void bakupProcessContent() throws Exception {
		File protoDir = new File("../src/main/java/xio");
		if (!protoDir.exists())
			return;
		for (File protoFile : protoDir.listFiles()) {
			StringBuffer sb = new StringBuffer();
			BufferedReader br = new BufferedReader(new FileReader(protoFile));
			String line;
			while ((line = br.readLine()) != null)
				sb.append(line).append("\n");
			String content = sb.toString();
			Matcher matcher = pattern.matcher(content);
			String filename = protoFile.getName();
			while (matcher.find()) {
				List<String> contents = filename2Contents.get(filename);
				if (contents == null) {
					contents = new ArrayList<>();
					filename2Contents.put(filename, contents);
				}
				contents.add(matcher.group());
			}
			br.close();
		}
	}
	
	private static void genProtobufFiles() throws Exception {
		Process process = Runtime.getRuntime().exec("/bin/sh protoc.sh");
		process.waitFor();
//		for (File protoFile : new File("./protocols/").listFiles()) {
//			if (!protoFile.getName().endsWith(".proto"))
//				continue;
//			exec(String.format("protoc --java_out=./src/main/java/ %s", protoFile.getAbsolutePath()));
//		}
	}
	
	private static void recoverProcessContent() throws Exception {
		if (filename2Contents.size() == 0)
			return;
		File protoDir = new File("../src/main/java/xio");
		for (File protoFile : protoDir.listFiles()) {
			StringBuffer sb = new StringBuffer();
			BufferedReader br = new BufferedReader(new FileReader(protoFile));
			String line;
			while ((line = br.readLine()) != null)
				sb.append(line).append("\n");
			String content = sb.toString();
			Matcher matcher = pattern.matcher(content);
			String filename = protoFile.getName();
			int i = 0;
			
			matcher.reset();
			boolean result = matcher.find();
			StringBuffer tmpSb = new StringBuffer();
			boolean tag = false;
			if (result) {
				do {
					List<String> contents = filename2Contents.get(filename);
					if (contents == null) {
						tag = true;
						break;
					}
					if (i + 1 > contents.size())
						break;
					matcher.appendReplacement(tmpSb, contents.get(i++));
					result = matcher.find();
				} while (result);
				matcher.appendTail(tmpSb);
			}
			if (tag) continue;
			br.close();
			FileWriter fw = new FileWriter(protoFile);
			fw.write(tmpSb.toString());
			fw.close();
		}
	}
	
	private static void genPropertiesFile() throws Exception {
		FileWriter fw = new FileWriter("../properties/protocol.properties");
		fw.write("#DO NOT EDIT THIS\n");
		File curDir = new File(".");
		int protoType = 1;
		for (File protoFile : curDir.listFiles()) {
			if (!protoFile.getName().endsWith(".proto"))
				continue;
			BufferedReader br = new BufferedReader(new FileReader(protoFile));
			String line;
			String packageName = null, outclassName = null, className = null;
			StringBuffer sb = new StringBuffer();
			while ((line = br.readLine()) != null) {
				if (line.trim().isEmpty())
					continue;
				sb.append(line);
				if (line.startsWith("option java_package")) {
					packageName = line.split("\"")[1];
				}
				if (line.startsWith("option java_outer_classname")) {
					outclassName = line.split("\"")[1];
				}
			}
			br.close();
			int leftBrackets = 0;
			String content = sb.toString();
			int preIndex = 0;
			for (int i = 0; i < content.length(); i++) {
				if (content.charAt(i) == '{') {
					if (leftBrackets == 0) {
						String tmp = content.substring(preIndex, i);
						String[] tmps = tmp.split(" ");
						int strNum = tmps.length;
						className = tmps[strNum - 1];
						StringBuffer innerSb = new StringBuffer();
						innerSb.append(protoType++).append("=");
						if (packageName != null && !packageName.isEmpty()) {
							innerSb.append(packageName).append(".");
						}
						innerSb.append(outclassName).append(".").append(className).append("\n");
						fw.write(innerSb.toString());
					}
					leftBrackets++;
				}
				if (content.charAt(i) == '}') {
					leftBrackets--;
					if (leftBrackets == 0) {
						preIndex = i;
					}
				}
			}
		}
		fw.write("#DO NOT EDIT THIS\n");
		fw.close();
	}
	
	public static void main(String[] args) throws Exception {
		bakupProcessContent();
		genProtobufFiles();
		recoverProcessContent();
		genPropertiesFile();
		File file = new File("../properties/protocol.properties");
		if (!file.exists())
			throw new NullPointerException("../properties/protocol.properties not exist!");
		Properties properties = new Properties();
		FileInputStream fis = new FileInputStream(file);
		properties.load(fis);
		fis.close();
		
		File javaFile = new File("../src/main/java/xio/GetIMessage.java");
		FileWriter fw = new FileWriter(javaFile);
		fw.write("// DO NOT EDIT THIS }}}\n");
		fw.write("// RPCGEN_IMPORT_END }}}\n");
		fw.write("package xio;\n\n");
		fw.write("public class GetIMessage {\n\n");
		fw.write("	private static org.apache.log4j.Logger LOG = org.apache.log4j.Logger.getLogger(GetIMessage.class);\n\n");
		fw.write("	public static IMessage getIMessage(byte[] data, int msgType) {\n");
		fw.write("		try {\n");
		for (Entry<Object, Object> entry : properties.entrySet()) {
			int id = Integer.parseInt((String) entry.getKey());
			String clsStr = (String) entry.getValue();
			fw.write(String.format("			if (msgType == %d) {\n", id));
			fw.write(String.format("				return %s.parseFrom(data);\n", clsStr));
			fw.write("			}\n");
		}
		fw.write("		} catch (Exception e) {\n");
		fw.write("			LOG.error(\"Parse protobuf failed! MsgType is: \" + msgType);\n");
		fw.write("			e.printStackTrace();\n");
		fw.write("		}\n");
		fw.write("		return null;\n");
		fw.write("	}\n\n");
		fw.write("}\n");
		fw.write("// DO NOT EDIT THIS }}}\n");
		fw.write("// RPCGEN_DEFINE_END }}}\n");
		fw.close();
		
		File interfaceFile = new File("../src/main/java/xio/IMessage.java");
		fw = new FileWriter(interfaceFile);
		fw.write("// DO NOT EDIT THIS }}}\n");
		fw.write("// RPCGEN_IMPORT_END }}}\n");
		fw.write("package xio;\n\n");
		fw.write("public interface IMessage {\n\n");
		fw.write("	void process(io.netty.channel.ChannelHandlerContext ctx);\n\n");
		fw.write("}\n");
		fw.write("// DO NOT EDIT THIS }}}\n");
		fw.write("// RPCGEN_DEFINE_END }}}\n");
		fw.close();
	}

}
