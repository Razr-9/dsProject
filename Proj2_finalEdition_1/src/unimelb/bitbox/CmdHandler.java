package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;

public class CmdHandler extends Thread {
	private ServerSocket listeningSocket = null;
	private ServerMain sm;
	private Socket socket = null;
	String keys[];
	private SecretKey aesKey;

	public CmdHandler(ServerMain sm) {
		int clientPort = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));
		keys = Configuration.getConfigurationValue("authorized_keys").split(",");
		this.sm = sm;
		try {
			// Create a server socket listening on ports get from configuration
			listeningSocket = new ServerSocket(clientPort);
			System.out.println("Server listening on port " + clientPort + " for a command");
			// Listen for incoming connections for ever
			start();
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void run() {
		// Accept an incoming client connection request
		try {
			while (true) {
				socket = listeningSocket.accept();
				String msg;
				// Output and Input Stream
				BufferedReader In = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
				BufferedWriter Out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
				msg = In.readLine();
				if (msg != null) {
					if (Document.parse(msg).get("command").equals("AUTH_REQUEST")) {
						String publicKey = null;
						System.out.println("Authorisation request received.");
						for (String key : keys) {
							if (key.split(" ")[2].equals(Document.parse(msg).get("identity"))) {
								publicKey = key.split(" ")[1];
								break;
							}
						}
						Document Doc = new Document();
						if (publicKey != null) {
							KeyGenerator keyGen = KeyGenerator.getInstance("AES");
							SecureRandom random = new SecureRandom();
							keyGen.init(128, random);
							aesKey = keyGen.generateKey();
							try {
								X509EncodedKeySpec pubX509 = new X509EncodedKeySpec(
										Base64.getDecoder().decode(publicKey));
								KeyFactory keyFactory = KeyFactory.getInstance("RSA");
								PublicKey pk = keyFactory.generatePublic(pubX509);
								byte[] encrypted = encrypt(aesKey.getEncoded(), pk, 1024, 11, "RSA/ECB/PKCS1Padding");
								String encrptedAES = Base64.getEncoder().encodeToString(encrypted);
								Doc.append("AES", encrptedAES);
								Doc.append("status", "true");
								Doc.append("message", "publickey found");
							} catch (Exception e) {
								e.printStackTrace();
							}
						} else {
							Doc.append("status", "false");
							Doc.append("message", "publickey not found");
						}
						Doc.append("command", "AUTH_RESPONSE");
						Out.write(Doc.toJson() + "\n");
						Out.flush();
						System.out.println("Authorisation response sent.");

						msg = In.readLine();
						if (msg != null) {
							byte[] payload = Base64.getDecoder().decode((String) Document.parse(msg).get("payload"));
							try {
								Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
								cipher.init(Cipher.DECRYPT_MODE, aesKey);
								byte[] decrypted = cipher.doFinal(payload);
								String request = new String(decrypted);
								System.out.println(request);
								Doc = new Document();
								if (Document.parse(request).get("command").equals("LIST_PEERS_REQUEST")) {
									ArrayList<Document> peers = new ArrayList<Document>();
									if (ServerMain.thClient != null) {
										for (int i = 0; i < ServerMain.thClient.length; i++) {
											if (ServerMain.thClient[i] != null && ServerMain.thClient[i].isAlive()) {
												Document peer = new Document();
												peer.append("host", ServerMain.thClient[i].Socket.getInetAddress()
														.getHostAddress());
												peer.append("port", ServerMain.thClient[i].Socket.getPort());
												peers.add(peer);
											}
										}
									}

									if (ServerMain.th != null) {
										for (int i = 0; i < ServerMain.th.length; i++) {
											if (ServerMain.th[i] != null && ServerMain.th[i].isAlive()) {
												Document peer = new Document();
												peer.append("host",
														ServerMain.th[i].Socket.getInetAddress().getHostAddress());
												peer.append("port", ServerMain.th[i].Socket.getPort());
												peers.add(peer);
											}
										}
									}

									if (ServerMain.rememberedPeers != null) {
										for (int i = 0; i < ServerMain.rememberedPeers.length; i++) {
											if (ServerMain.rememberedPeers[i][0] != null) {
												Document peer = new Document();
												peer.append("host", ServerMain.rememberedPeers[i][0]);
												peer.append("port", ServerMain.rememberedPeers[i][1]);
												peers.add(peer);
											}
										}
									}
									Doc.append("peers", peers);
									Doc.append("command", "LIST_PEERS_RESPONSE");
								} else if (Document.parse(request).get("command").equals("CONNECT_PEER_REQUEST")) {
									String host = Document.parse(request).get("host").toString();
									String port = Document.parse(request).get("port").toString();
									boolean status = false;
									if (Configuration.getConfigurationValue("mode").equals("tcp")) {
										Client t = new Client((host + ":" + port), sm.fileSystemManager);
										Thread.sleep(1000);
										status = t.thR()[0].isAlive();
									} else if (Configuration.getConfigurationValue("mode").equals("udp")) {
										for (int j = 0; j < Integer
												.parseInt(Configuration.getConfigurationValue("udpRetries")); j++) {
											new UDPResponse().sendHandshake(host, Integer.parseInt(port));
											Thread.sleep(1000);
											if (UDPResponse.status) {
												status = UDPResponse.status;
												break;
											}
										}
									}
									if (status) {
										Doc.append("status", status);
										Doc.append("message", "connected to peer");
									} else {
										Doc.append("status", status);
										Doc.append("message", "conncetion failed");
									}
									UDPResponse.status = false;
									Doc.append("host", host);
									Doc.append("port", Integer.parseInt(port));
									Doc.append("command", "CONNECT_PEER_RESPONSE");
								} else if (Document.parse(request).get("command").equals("DISCONNECT_PEER_REQUEST")) {
									String host = Document.parse(request).get("host").toString();
									String port = Document.parse(request).get("port").toString();
									boolean status = false;
									status = ServerMain.Disconnection(host, port);
									if (status) {
										Doc.append("status", status);
										Doc.append("message", "disconnected from peer");
									} else {
										Doc.append("status", status);
										Doc.append("message", "connection not active");
									}
									Doc.append("host", host);
									Doc.append("port", Integer.parseInt(port));
									Doc.append("command", "DISCONNECT_PEER_RESPONSE");
								} else {
									Doc.append("command", "INVALID_RESPONSE");
								}
								Doc.append("status", "true");
								Cipher cipherAES = Cipher.getInstance("AES/ECB/PKCS5Padding");
								cipherAES.init(Cipher.ENCRYPT_MODE, aesKey);
								byte[] encrypted = cipherAES.doFinal(Doc.toJson().toString().getBytes());
								String encrptedAES = Base64.getEncoder().encodeToString(encrypted);
								Doc = new Document();
								Doc.append("payload", encrptedAES);
								Out.write(Doc.toJson() + "\n");
								Out.flush();
								System.out.println("Command response sent.");
								socket.close();
							} catch (NoSuchPaddingException e) {
								e.printStackTrace();
							} catch (InvalidKeyException e) {
								e.printStackTrace();
							} catch (IllegalBlockSizeException e) {
								e.printStackTrace();
							} catch (BadPaddingException e) {
								e.printStackTrace();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					} else {
						System.out.println("Invalid request received.");
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}

	public static byte[] encrypt(byte[] plainBytes, PublicKey publicKey, int keyLength, int reserveSize,
			String cipherAlgorithm) throws Exception {
		int keyByteSize = keyLength / 8;
		int encryptBlockSize = keyByteSize - reserveSize;
		int nBlock = plainBytes.length / encryptBlockSize;
		if ((plainBytes.length % encryptBlockSize) != 0) {
			nBlock += 1;
		}
		ByteArrayOutputStream outbuf = null;
		try {
			Cipher cipher = Cipher.getInstance(cipherAlgorithm);
			cipher.init(Cipher.ENCRYPT_MODE, publicKey);

			outbuf = new ByteArrayOutputStream(nBlock * keyByteSize);
			for (int offset = 0; offset < plainBytes.length; offset += encryptBlockSize) {
				int inputLen = plainBytes.length - offset;
				if (inputLen > encryptBlockSize) {
					inputLen = encryptBlockSize;
				}
				byte[] encryptedBlock = cipher.doFinal(plainBytes, offset, inputLen);
				outbuf.write(encryptedBlock);
			}
			outbuf.flush();
			return outbuf.toByteArray();
		} catch (Exception e) {
			throw new Exception("ENCRYPT ERROR:", e);
		} finally {
			try {
				if (outbuf != null) {
					outbuf.close();
				}
			} catch (Exception e) {
				outbuf = null;
				throw new Exception("CLOSE ByteArrayOutputStream ERROR:", e);
			}
		}
	}
}
