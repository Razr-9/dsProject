package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

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
							} catch (Exception e) {
								// TODO Auto-generated catch block
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
								System.out.println(new String(decrypted));

							} catch (NoSuchPaddingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (InvalidKeyException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IllegalBlockSizeException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (BadPaddingException e) {
								// TODO Auto-generated catch block
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
			// TODO Auto-generated catch block
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
