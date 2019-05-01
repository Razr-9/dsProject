//package unimelb.bitbox;
//
//import java.io.BufferedReader;
//import java.io.BufferedWriter;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.io.OutputStreamWriter;
//import java.net.Socket;
//import java.net.SocketException;
//import java.nio.ByteBuffer;
//import java.security.NoSuchAlgorithmException;
//import java.util.Base64;
//
//import unimelb.bitbox.util.Document;
//import unimelb.bitbox.util.FileSystemManager;
//
//public class FIleByte extends Thread{
//	BufferedReader In;
//	BufferedWriter Out;
//	String Msg = null;
//	Socket Socket;
//	FileSystemManager fileSystemManager;
//	
//	public FIleByte(Socket Socket, FileSystemManager fileSystemManager) {
//		try {
//		In = new BufferedReader(new InputStreamReader(Socket.getInputStream(), "UTF-8"));
//		Out = new BufferedWriter(new OutputStreamWriter(Socket.getOutputStream(), "UTF-8"));
//		} catch (SocketException ex) {
//			ex.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		} 
//		this.Socket = Socket;
//		this.fileSystemManager = fileSystemManager;
//		start();
//	}
//	
//	public void run(){
//		Request();
//		
//	}
//	
//	public void Request() {
//		String pathName = Document.parse(message).get("pathName").toString();
//		String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
//		String md5 = Document.parse(fileDes).get("md5").toString();
//		long length = Document.parse(message).getLong("length");
//		long position = Document.parse(message).getLong("position");
//		Document Doc = new Document();
//		Doc.append("command", "FILE_BYTES_RESPONSE");
//		Doc.append("fileDescriptor", fileDes);
//		Doc.append("pathName", pathName);
//		try {
//			if(fileSystemManager.fileNameExists(pathName, md5)) {
//				ByteBuffer content = fileSystemManager.readFile(md5, position, length);
//				ByteBuffer src = Base64.getEncoder().encode(content);
//				if(content == null) { //no such file
//					Doc.append("message", "unsuccessful read");
//					Doc.append("status",false);
//				}
//				Doc.append("position", position);
//				Doc.append("length", length);
//				Doc.append("message", "successful read");
//				Doc.append("status", true);
//			}else {// file name and content don't exist
//				Doc.append("message", "unsuccessful read");
//				Doc.append("status",false);
//			}
//			System.out.println(Doc.toJson());
//			Out.write(Doc.toJson()+"\n");
//			Out.flush();
//			
//		} catch (NoSuchAlgorithmException e) {
//			e.printStackTrace();
//		}
//	}
//	
//	public void Response() {
//		String pathName = Document.parse(message).get("pathName").toString();
//		String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
//		String md5 = Document.parse(fileDes).get("md5").toString();
//		long length = Document.parse(message).getLong("length");
//		long position = Document.parse(message).getLong("position");
//		ByteBuffer src = (ByteBuffer) Document.parse(message).get("content");
//		ByteBuffer content = Base64.getDecoder().decode(src);
//		Document Doc = new Document();
//		if(fileSystemManager.writeFile(pathName, content, position)) {
//			try {
//				if(fileSystemManager.checkWriteComplete(pathName)) {
//					System.out.println("Write already done");
//				}else {
//					System.out.println("Already transfered"+ (length + position) + "bytes");
//				}
//			} catch (NoSuchAlgorithmException e) {
//				e.printStackTrace();
//			}
//		}else {
//			System.out.println("there was no associated file loader for the given name.");
//		}
//	}
//}
