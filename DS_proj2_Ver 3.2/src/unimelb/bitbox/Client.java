package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;

class Client {
	private static Logger log = Logger.getLogger(Client.class.getName());
	Socket Socket = null;
	String[] hostPorts;
	int len;
	Threads[] th;
	static ArrayList<HostPort> BFS = new ArrayList<HostPort>();
	static int bfsCount = 0;

	public Client(String peers, FileSystemManager fileSystemManager) {
		System.out.println("Attempting to connect to other peers");
		hostPorts = peers.split(",");
		// Create a stream socket bounded to any port and connect it to the
		// socket bound to localhost on port 4444
		len = hostPorts.length;
		th = new Threads[len];
		int i = 1;
		while (len > 0) {
			HostPort HP = new HostPort(hostPorts[len - 1]);
			try {
				boolean exist = false;
				for (int i1 = 0; i1 < th.length; i1++) {
					if (th[i1] != null && th[i1].isAlive() && th[i1].Socket.getPort() == HP.port) {
						exist = true;
						System.out.println("Connection exists");
						len--;
						i++;
						break;
					}
				}
				if (!exist) {
					Socket = new Socket(HP.host, HP.port);
					System.out.println("Connecting to peer " + i + " ...");
					th[len - 1] = new Threads(Socket, i, "Client", fileSystemManager);
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (!th[len - 1].isAlive()) {
						int A = 0;
						while (A < bfsCount) {
							Socket = new Socket(BFS.get(A).host, BFS.get(A).port);
							th[len - 1] = new Threads(Socket, i, "Client", fileSystemManager);
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							if (th[len - 1].isAlive()) {
								bfsCount = 0;
								BFS.clear();
								break;
							}
							A++;
						}
						len--;
						i++;
					} else {
						len--;
						i++;
					}
				}
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (ConnectException e) {
				System.out.println("Connection to peer " + i + " failed");
				len--;
				i++;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public Threads[] thR() {
		return th;
	}

	public static void main(String[] args) throws IOException, NumberFormatException, NoSuchAlgorithmException {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tc] %2$s %4$s: %5$s%n");
		log.info("BitBox Client starting...");
		CmdLineArgs cArg = new CmdLineArgs();
		CmdLineParser parser = new CmdLineParser(cArg);
		try {
			// Parse the arguments
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			// Print the usage to help the user understand the arguments expected
			// by the program
			parser.printUsage(System.err);
		}
		String msg;
		try (Socket socket = new Socket(cArg.getServer().host, cArg.getServer().port);) {
			// Output and Input Stream
			BufferedReader In = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
			BufferedWriter Out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
			if (cArg.isValid()) {
				// AUTH_REQUEST
				Document Doc = new Document();
				Doc.append("command", "AUTH_REQUEST");
				if (cArg.getIdentity() != null) {
					Doc.append("identity", cArg.getIdentity());
				} else {
					System.out.println("Identity not specified, please use -i option to input the identity.");
					System.exit(0);
				}
				Out.write(Doc.toJson() + "\n");
				Out.flush();
				System.out.println("Authorisation request sent.");

				msg = In.readLine();
				if (msg != null) {
					if (Document.parse(msg).get("command").equals("AUTH_RESPONSE")) {
						System.out.println("Authorisation resoponse received.");
						if(Document.parse(msg).get("status").equals("true")) {
							System.out.println(msg);
						} else {
							System.out.println("Public Key not found.");
							socket.close();
							System.exit(0);
						}
					} else {
						System.out.println("Invalid response received.");
						socket.close();
						System.exit(0);
					}
				}
				
				Doc = new Document();
				Doc.append("command", cArg.getCommand());
				PrivateKey privateKey = getPriKey("private_key.pem","RSA");  
				byte[] aes = Base64.getDecoder().decode((String)Document.parse(msg).get("AES"));
				try {
					byte[] decrypted= decrypt(aes, privateKey, 2048, 11, "RSA/ECB/PKCS1Padding");
					Key aesKey = new SecretKeySpec(decrypted, "AES");
					Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
					cipher.init(Cipher.ENCRYPT_MODE, aesKey);
					byte[] encrypted = cipher.doFinal(Doc.toJson().toString().getBytes());
					String encrptedAES = Base64.getEncoder().encodeToString(encrypted);
					Doc = new Document();
					Doc.append("payload", encrptedAES);
					Out.write(Doc.toJson() + "\n");
					Out.flush();
					System.out.println("Command request sent.");
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}  
			}
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static byte[] decrypt(byte[] encryptedBytes, PrivateKey privateKey, int keyLength, int reserveSize, String cipherAlgorithm) throws Exception {  
        int keyByteSize = keyLength / 8;  
        int decryptBlockSize = keyByteSize - reserveSize;  
        int nBlock = encryptedBytes.length / keyByteSize;  
        ByteArrayOutputStream outbuf = null;  
        try {  
            Cipher cipher = Cipher.getInstance(cipherAlgorithm);  
            cipher.init(Cipher.DECRYPT_MODE, privateKey);  
  
            outbuf = new ByteArrayOutputStream(nBlock * decryptBlockSize);  
            for (int offset = 0; offset < encryptedBytes.length; offset += keyByteSize) {  
                int inputLen = encryptedBytes.length - offset;  
                if (inputLen > keyByteSize) {  
                    inputLen = keyByteSize;  
                }  
                byte[] decryptedBlock = cipher.doFinal(encryptedBytes, offset, inputLen);  
                outbuf.write(decryptedBlock);  
            }  
            outbuf.flush();  
            return outbuf.toByteArray();  
        } catch (Exception e) {  
            throw new Exception("DEENCRYPT ERROR:", e);  
        } finally {  
            try{  
                if(outbuf != null){  
                    outbuf.close();  
                }  
            }catch (Exception e){  
                outbuf = null;  
                throw new Exception("CLOSE ByteArrayOutputStream ERROR:", e);  
            }  
        }  
    }  
	
	public static PrivateKey getPriKey(String privateKeyPath,String keyAlgorithm){  
        PrivateKey privateKey = null;  
        InputStream inputStream = null;  
        try {  
        	if(inputStream==null){  
                System.out.println("inputStream path doesn't exist");  
            }  
            inputStream = new FileInputStream(privateKeyPath);   
            privateKey = getPrivateKey(inputStream,keyAlgorithm);  
        } catch (Exception e) {  
            System.out.println("PrivateKey input failed.");  
        } finally {  
            if (inputStream != null){  
				try {  
				inputStream.close();  
                }catch (Exception e){  
                    System.out.println("Closing input stream failed.");  
                }  
            }  
        }  
        return privateKey;  
    }  
	
	public static PrivateKey getPrivateKey(InputStream inputStream, String keyAlgorithm) throws Exception {  
        try {  
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));  
            StringBuilder sb = new StringBuilder();  
            String readLine = null;  
            while ((readLine = br.readLine()) != null) {  
                if (readLine.charAt(0) == '-') {  
                    continue;  
                } else {  
                    sb.append(readLine);  
                }  
            }  
            String ss = sb.toString();
            byte[] bb = Base64.getDecoder().decode(ss);
            PKCS8EncodedKeySpec priPKCS8 = new PKCS8EncodedKeySpec(bb);  
            KeyFactory keyFactory = KeyFactory.getInstance(keyAlgorithm);   
            PrivateKey privateKey = keyFactory.generatePrivate(priPKCS8);  
            return privateKey;  
        } catch (Exception e) {  
            throw new Exception("Failure in reading the key" ,e);  
        }  finally {  
            try {  
                if (inputStream != null) {  
                    inputStream.close();  
                }  
            } catch (IOException e) {  
                inputStream = null;  
                throw new Exception("Failure when closing the input stream:", e);  
            }  
        }  
    }  
}
