package com.example.myfirstapp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

public class BluetoothService {
	private static final String TAG = "com.example.myfirstapp";
	
	public static final int ESTAT_CAP = 0;
	public static final int ESTAT_CONNECTAT = 1;
	public static final int ESTAT_FENT_CONNEXIO = 2;
	public static final int ESTAT_ACCEPTANT_PETICIONS = 3;
	
	public static final int MSG_LLEGIR = 11;
	public static final int MSG_ESCRIURE = 12;
	
	private final Handler handler;
	private BluetoothSocket socket;
	
	private int estat;

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
		
	}
	
	public BluetoothService(Handler handler) {
		this.handler = handler;
	}
	
	public void setEstat(int estatConnectat) {
		this.estat = estatConnectat;			
	}
}
