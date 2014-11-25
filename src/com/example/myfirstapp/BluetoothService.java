package com.example.myfirstapp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.util.Log;

public class BluetoothService {
	private static final boolean DEBUG = true;
	private static final String TAG = "com.example.myfirstapp";
	
	private final Handler handler;
	private final Context context;
	private BluetoothAdapter bAdapter;
	
	public static final String NOM_SEGUR = "BluetoothServiceSecure";
	public static final String NOM_INSEGUR = "BluetoothServiceInsecure";
	public static UUID UUID_SEGUR; //= UUID.fromString("org.danigarcia.examples.bluetooth.BluetoothService.Secure");
	public static UUID UUID_INSEGUR; //= UUID.fromString("org.danigarcia.examples.bluetooth.BluetoothService.Insecure");

	public static final int ESTAT_CAP = 0;
	public static final int ESTAT_CONNECTAT = 1;
	public static final int ESTAT_FENT_CONNEXIO = 2;
	public static final int ESTAT_ATENENT_PETICIONS = 3;
	
	public static final int MSG_CANVI_ESTAT = 10;
	public static final int MSG_LLEGIR = 11;
	public static final int MSG_ESCRIURE = 12;
	public static final int MSG_ATENDRE_PETICIONS = 13;
	public static final int MSG_ALERTA = 14;
	
	private int estat;
	private FilConnexio filConnexio = null;
	private FilServidor filServidor = null;
	private FilClient filClient = null;
		
	
	public BluetoothService(Context context, Handler handler, BluetoothAdapter adapter) {
		debug("BluetoothService():", "iniciant metode");
		
		this.context = context; 
		this.handler = handler;
		this.bAdapter = adapter;
		this.estat = ESTAT_CAP;
		
		UUID_SEGUR = generarUUID();
		UUID_INSEGUR = generarUUID();
	}
	
	public void setEstat(int estatConnectat) {
		this.estat = estatConnectat;
		handler.obtainMessage(MSG_CANVI_ESTAT, estat, -1).sendToTarget();
	}
	
	public int getEstat() {
		return this.estat;
	}
	
	public String getNomDispositiu() {
		String nom = "";
		if (estat == ESTAT_CONNECTAT) {
			if (filConnexio != null)
				nom = filConnexio.getName();
		}
		return nom;
	}
	
	public void debug(String metode, String missatge)
	{
		if(DEBUG)
			Log.d(TAG, metode + ": " + missatge);
	}
	
	private UUID generarUUID() {
		final TelephonyManager tManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
		final String deviceId = String.valueOf(tManager.getDeviceId());
		final String simSerialNumber = tManager.getSimSerialNumber();
		final String androidId = android.provider.Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
		UUID uuid = new UUID(androidId.hashCode(), ((long)deviceId.hashCode() << 32) | simSerialNumber.hashCode());
		uuid = new UUID((long)1000, (long)23);
		
		return uuid;
	}
	
	// inicia el servei, creant un FilServidor que es dedicara a atendre les peticions de connexio
	public synchronized void iniciarServei() {
		debug("iniciarServei()", "Iniciant metode");
		
		// si s'esta intentant fer una connexio mitjançant un fil client, es cancela la connexio
		if (filClient != null) {
			filClient.cancelarConnexio();
			filClient = null;
		}
		
		// si existeix una connexio, es cancela
		if (filConnexio != null) {
			filConnexio.cancelarConexion();
			filConnexio = null;
		}
		
		// arranquem el fil servidor perque comenci a escoltar peticions
		if (filServidor == null) {
			filServidor = new FilServidor();
			filServidor.start();
		}
		
		debug("iniciarServei():", "finalitzant metode");
	}
	
	public void finalitzarServei() {
		debug("finalitzarServei()", "iniciant metode");
		
		if (filClient != null) 
			filClient.cancelarConnexio();
		if (filConnexio != null)
			filConnexio.cancelarConexion();
		if (filServidor != null)
			filServidor.cancelarConnexio();
		
		filClient = null;
		filConnexio = null;
		filServidor = null;
		
		setEstat(ESTAT_CAP);
		debug("finalitzarServei()", "finalitzant metode");
	}
	
	// instanciem un fil connector
	public synchronized void solicitarConnexio(BluetoothDevice dispositiu) {
		debug("solicitarConnexio():","iniciant metode");
		
		// comprovem si existia un intent de connexio
		// si es el cas, es cancela i es torna a iniciar el proces
		if (estat == ESTAT_FENT_CONNEXIO) {
			if (filClient != null) {
				filClient.cancelarConnexio();
				filClient = null;
			}
		}
		
		// si existia una connexio oberta, es tanca i s'inicia una de nova
		if (filConnexio != null) {
			filConnexio.cancelarConexion();
			filConnexio = null;
		}
		
		// s'instancia un nou fil connector, encarregat de solicitar una connexio al servidor
		// que sera l'altra part
		filClient = new FilClient(dispositiu);
		filClient.start();
		
		setEstat(ESTAT_FENT_CONNEXIO);
		debug("solicitarConnexio():","finalitzant metode");
	}
	
	public synchronized void realitzarConnexio(BluetoothSocket socket, BluetoothDevice dispositiu) {
		debug("realitzarConnexio():", "iniciant metode");
		filConnexio = new FilConnexio(socket);
		filConnexio.start();
		debug("realitzarConnexio():", "finalitzant metode");
	}
	
	// sincronitza l'objecte amb el fil FilConnexio i invoca el seu metode escriure()
	// per enviar el missatge a traves del flux de sortida del socket
	public int enviar(byte[] buffer) {
		debug("enviar():", "iniciant metode");
		FilConnexio tmpConnexio;
		
		synchronized (this) {
			if(estat != ESTAT_CONNECTAT) {
				return -1;
			}
			tmpConnexio = filConnexio;
		}
		tmpConnexio.escriure(buffer);
		
		return buffer.length;
	}
	
	// fil que fa, des de servidor, s'encarrega d'escoltar connexions entrantes i
	// crear un fil que gestioni la connexio quan aixo passi
	// l'altra part haura de ser solicitar la connexio a traves d'un FilClient
	private class FilServidor extends Thread {
		private final BluetoothServerSocket serverSocket;
		
		public FilServidor() {
			debug("FilServidor.new()","iniciant metode");
			BluetoothServerSocket tmpServerSocket = null;
			
			// creem un socket per escoltar les peticions de connexio
			try {
				tmpServerSocket = bAdapter.listenUsingRfcommWithServiceRecord(NOM_SEGUR, UUID_SEGUR);
			} catch (IOException e) {
				Log.e(TAG, "FilServidor(): error a l'obrir el socket servidor", e);
			}
			serverSocket = tmpServerSocket;
		}
		
		public void run() {
			debug("FilServidor.run()","iniciant metode");
			BluetoothSocket socket = null;
			
			setName("FilServidor");
			setEstat(ESTAT_ATENENT_PETICIONS);
			// el fil es mantindra en estat d'espera ocupada acceptant connexions entrants
			// sempre i quan no existeixi una connexio activa en el moment que entri una
			// nova connexio
			while(estat != ESTAT_CONNECTAT) {
				try {
					socket = serverSocket.accept();
				} 
				catch (IOException e) {
					Log.e(TAG, "FilServidor.run(): error al acceptar connexions entrants", e);
					break;
				}
				
				// si el socket te valor sera perque un client ha sol·licitat la connexio
				if (socket != null) {
					// fem un lock de l'objecte
					synchronized (BluetoothService.this) {
						switch (estat) {
							case ESTAT_ATENENT_PETICIONS:
							case ESTAT_FENT_CONNEXIO:
							{
								debug("FilServidor.run()", estat == ESTAT_ATENENT_PETICIONS ? "Atenent peticions" : "Realitzant connexio");
								// estat esperat. Es crea el fil de connexio que rebra
								// i enviara els missatges
								realitzarConnexio(socket, socket.getRemoteDevice());
								break;	
							}
							case ESTAT_CAP:
							case ESTAT_CONNECTAT:
							{
								// no preparat o connexio ja feta. Es tanca el socket
								try {
									debug("FilServidor.run()", estat == ESTAT_CAP ? "Cap" : "Connectat");
									socket.close();
								}
								catch (IOException e) {
									Log.e(TAG, "FilServidor.run(): socket.close(). Error al tancar el socket", e);
								}
								break;
							}
							default:
								break;
						}
					}
				}
			}	
		}
		public void cancelarConnexio() {
			try {
				serverSocket.close();
			}
			catch (IOException e) {
				Log.e(TAG, "FilServidor.cancelarConnexio(): Error al tancar el socket", e);
			}
		}
	}
	
	// fil encarregat de solicitar una connexio a un dispositiu que esta fent de FilServidor
	private class FilClient extends Thread {
		private final BluetoothDevice dispositiu;
		private final BluetoothSocket socket;
		
		public FilClient(BluetoothDevice dispositiu) {
			BluetoothSocket tmpSocket = null;
			this.dispositiu = dispositiu;
			
			// obtinc un socket pel dispositiu amb el que es vol connectar
			try {
				tmpSocket = dispositiu.createRfcommSocketToServiceRecord(UUID_SEGUR);
			} catch (IOException e) {
				Log.e(TAG, "FilClient.FilClient(): error a l'obrir el socket", e);
			}
			socket = tmpSocket;
		}
		
		public void run() {
			setName("FilClient");
			if (bAdapter.isDiscovering()) 
				bAdapter.cancelDiscovery();
			
			try {
				socket.connect();
				setEstat(ESTAT_FENT_CONNEXIO);
			}
			catch (IOException e) {
				Log.e(TAG, "FilClient.run(): socket.connect(): error fent la connexio", e);
			}
			setEstat(ESTAT_CAP);
			
			// reiniciem el fil client ja que no el necessitarem mes
			synchronized (BluetoothService.this) {
				filClient = null;
			}
			
			// fem la connexio
			realitzarConnexio(socket, dispositiu);
		}
		
		public void cancelarConnexio() {
			debug("cancelarConnexio()", "Iniciant metode");
	        try {
	            socket.close();
	        }
	        catch(IOException e) {
	            Log.e(TAG, "FilClient.cancelarConnexio(): Error al tancar el socket", e);
	        }
	        setEstat(ESTAT_CAP);
		}
	}
	
	// fil encarregat de mantenir la connexio i realitzar les lectures
	// dels missatges intercanviats entre dispositius
	private class FilConnexio extends Thread {
		private final BluetoothSocket socket; // socket
		private final InputStream inputStream; // fluxe d'entrada
		private final OutputStream outputStream; // fluxe de sortida
		
		public FilConnexio(BluetoothSocket socket) {
			this.socket = socket;
			setName(socket.getRemoteDevice().getName() + " [" + socket.getRemoteDevice().getAddress()+"] ");
			
			// utilitzem variables temporals degut a que els atributs son declarats com a final
			// i no seria possible assignar valors posteriorment si falla la connexio
			InputStream tmpInputStream = null;
			OutputStream tmpOutputStream = null;
			
			// obtinc els fluxes d'entrada i sortida del socket
			try {
				tmpInputStream = socket.getInputStream();
				tmpOutputStream = socket.getOutputStream();
			} catch (IOException e) {
				Log.e(TAG, "FilConnexio(): Error al obtenir fluxes");
			}
			
			inputStream = tmpInputStream;
			outputStream = tmpOutputStream;
		}
		
		// metode principal del fil, encarregat de realitzar les lectures
		public void run() {
			byte[] buffer = new byte[1024];
			int bytes;
			setEstat(ESTAT_CONNECTAT);
			
			// mentres es mantingui la conexxio, el fil es mante en espera ocupat llegint el flux d'entrada
			while(true) {
				try{
					// llegim el flux d'entrada del socket
					bytes = inputStream.read(buffer);
					
					// enviem la informacio a la activitat a traves del handler
					// el metode handleMessage sera l'encarregat de rebre el missatge
					// i mostrar les dades rebudes en el textview
					handler.obtainMessage(MSG_LLEGIR, bytes, -1, buffer).sendToTarget();
				}
				catch(IOException e) {
					Log.e(TAG, "FilConnexio.run(): error al fer la lectura", e);
				}
			}
		}
		
		public void escriure(byte[] buffer) {
			try {
				// escrivim en el fluxe de sortida del socket
				// el metode handlemessage sera l'encarregat de rebre 
				// i mostrar les dades enviades en el Toast
				outputStream.write(buffer);
				handler.obtainMessage(MSG_ESCRIURE, -1, -1, buffer);
			}
			catch(IOException e) {
				Log.e(TAG, "FilConnexio.escriure(): error al fer l'escriptura", e);
			}
		}
		
		public void cancelarConexion() {
			debug("cancelarConnexio()", "Iniciant metode");
			try {
				socket.close();
			}
			catch(IOException e) {
				Log.e(TAG, "FilClient.cancelarConnexio(): Error al tancar el socket", e);
			}
			setEstat(ESTAT_CAP);
		}
	}
}
