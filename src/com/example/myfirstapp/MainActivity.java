package com.example.myfirstapp;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements android.view.View.OnClickListener, OnItemClickListener
{
	private static final String TAG = "com.example.myfirstapp";
	private static final String ALERTA = "alerta";


	// boto de bluetooth
	private Button btnBluetooth;
	
	// boto de buscar dispositius
	private Button btnBuscarDispositiu;
	
	// boto d'enviar
	private Button btnEnviar;
	
	// adaptador bluetooth del telefon
	private BluetoothAdapter bAdapter;
	
	// servei de bluetooth
	private BluetoothService servei;
	
	// llista de dispositius descoberts
	private ArrayList<BluetoothDevice> arrayDevices;
	private ArrayAdapter<?> arrayAdapter;
	
	private ListView lvDispositius;
	private TextView tvMissatge;
	
	private static final int REQUEST_ENABLE_BT = 1;
	
	// broadcastreceiver => serveix per detectar si l'estat del bluetooth
	// canvia fora de la nostra app (ex. usuari l'apaga o activa)
	private final BroadcastReceiver bReceiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			
			//filtrem l'accio. Ens interessa detectar BluetoothAdapter.ACTION_STATE_CHANGED
			if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
				final int estat = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
				switch (estat) {
					// Apagat
					case BluetoothAdapter.STATE_OFF:
					{
						Log.v(TAG, "onReceive: Apagant");
						((Button)findViewById(R.id.btnBluetooth)).setText(R.string.ActivarBluetooth);
						((Button)findViewById(R.id.btnBuscarDispositiu)).setEnabled(false);
						buidarLlistaDispositius();
						break;	
					}
	
					// Ences
					case BluetoothAdapter.STATE_ON:
						// canviem el text del boto
						((Button)findViewById(R.id.btnBluetooth)).setText(R.string.DesactivarBluetooth);
						((Button)findViewById(R.id.btnBuscarDispositiu)).setEnabled(true);
						
						// llancem intent de solicitud de visibilitat bluetooth al qual afegim un
						// parell clau-valor que indicara la duracio de l'estat, en aquest cas 120 segons
						Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
						discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600);
						startActivity(discoverableIntent);
						
						break;
					default:
						break;
				}
			}
			// accio a executar quan es trobi un dispositiu
			else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// si la llista no ha sigut inicialitzada, la inicialitzem
				if (arrayDevices == null) {
					arrayDevices = new ArrayList<BluetoothDevice>();
				}
				
				// extrec el dispoitiu del intent mitjançant la clau BluetoothDevice.EXTRA_DEVICE
				BluetoothDevice dispositiu = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				
				// l'afegim a la llista
				arrayDevices.add(dispositiu);
				
				// li assignem un nom de l'estil NomDispositiu [00:11:22:33:44]
				String descripcioDispositiu = dispositiu.getName() + " [" + dispositiu.getAddress() +"]";
				
				// mostrem que hem trobat el dispositiu per Toast
				Toast.makeText(getBaseContext(), getString(R.string.DetectatDispositiu) + ": " + descripcioDispositiu, Toast.LENGTH_SHORT).show();
			}
			
			// accio a executar quan s'hagi finalitzat la recerca de dispositius
			else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				// instanceim un nou adaptador pel ListView mitjançant la classe BluetoothDeviceArrayAdapter
				arrayAdapter = new BluetoothDeviceArrayAdapter(getBaseContext(), android.R.layout.simple_list_item_2, arrayDevices);
				
				lvDispositius.setAdapter(arrayAdapter);
				int ndisp = (arrayDevices != null ? arrayDevices.size() : 0); 
				
				//TODO: missatges del toast al fitxer strings.xml?
				if (ndisp == 1) 
					Toast.makeText(getBaseContext(), "Fi de la cerca, he trobat: 1 dispositiu" , Toast.LENGTH_SHORT).show();
				else if (ndisp > 1) 
					Toast.makeText(getBaseContext(), "Fi de la cerca, he trobat: " + ndisp + " dispositius", Toast.LENGTH_SHORT).show();
				else 
					Toast.makeText(getBaseContext(), "Fi de la cerca, no he trobat cap dispositiu",	Toast.LENGTH_SHORT).show();
				
				
			}
			else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
				buidarLlistaDispositius();
				Toast.makeText(getBaseContext(), "Començant cerca dispositius", Toast.LENGTH_SHORT).show();
			}
		}

		private void buidarLlistaDispositius() {
			if (arrayAdapter != null)
				arrayAdapter.clear();
		}
	};
	
	// handler que obtindra la infromacio des de BluetoothService
	private final Handler handler = new Handler() {
		
		@Override
		public void handleMessage(Message msg) {
			byte[] buffer = null;
			String missatge = null;
			
			// atenem el tipus de missatge
			switch(msg.what) {
				// missatge de lectura: es mostra en el TextView
				case BluetoothService.MSG_LLEGIR: {
					buffer = (byte[])msg.obj;
					
					missatge = new String(buffer, 0, msg.arg1);
					tvMissatge.setText(missatge);	          
			        break;
				}
				
				// missatge d'escriputra: es mostra en el Toast
				case BluetoothService.MSG_ESCRIURE: {
					buffer = (byte[])msg.obj;
					missatge = new String(buffer);
					missatge = getString(R.string.EnviantMissatge) + ": " + missatge;
					Toast.makeText(getApplicationContext(), missatge, Toast.LENGTH_SHORT).show();
					break;
				}
				
				// missatge de canvi d'estat
				case BluetoothService.MSG_CANVI_ESTAT: {
					switch (msg.arg1) {
						case BluetoothService.ESTAT_ATENENT_PETICIONS:
							break;
							
						case BluetoothService.ESTAT_CONNECTAT: {
							missatge = getString(R.string.ConnexioActual) + " " + servei.getNomDispositiu();
							Toast.makeText(getApplicationContext(), missatge, Toast.LENGTH_SHORT).show();
							btnEnviar.setEnabled(true);
							break;
						}
						
						case BluetoothService.ESTAT_FENT_CONNEXIO: {
							missatge = getString(R.string.Connectant);
							Toast.makeText(getApplicationContext(), missatge, Toast.LENGTH_SHORT).show();
							btnEnviar.setEnabled(false);
							break;
						}
						
						case BluetoothService.ESTAT_CAP: {
							missatge = getString(R.string.SenseConnexio);
							Toast.makeText(getApplicationContext(), missatge, Toast.LENGTH_SHORT).show();
							tvMissatge.setText(missatge);
							btnEnviar.setEnabled(false);
							break;
						}
						
						default:
							break;
					}
					break;
				}
				
				//missatge d'alerta: es mostrara en el Toast
				case BluetoothService.MSG_ALERTA: {
					missatge = msg.getData().getString(ALERTA);
					Toast.makeText(getApplicationContext(), missatge, Toast.LENGTH_SHORT).show();
					break;
				}
				
				default: 
					break;
			}
		}
	};
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        btnEnviar = (Button)findViewById(R.id.btnEnviar);
        btnBluetooth = (Button)findViewById(R.id.btnBluetooth);
        btnBuscarDispositiu = (Button)findViewById(R.id.btnBuscarDispositiu);
        lvDispositius = (ListView)findViewById(R.id.lvDispositius);
        tvMissatge = (TextView)findViewById(R.id.tvMissatge);
        configurarAdaptadorBluetooth();
        registrarEventosBluetooth();
    }
    
    public void configurarAdaptadorBluetooth() {
    	// Obtenim l'adaptador Bluetooth. Si val nULL, significa
    	// que el dispositiu no te Bluetooth, per tant deshabilitarem
    	// el boto que permet encendre/apagar el Bluetooth,
    	bAdapter = BluetoothAdapter.getDefaultAdapter();

    	if (bAdapter == null) {
    		btnBluetooth.setEnabled(false);
    		btnBuscarDispositiu.setEnabled(false);
    		return;
    	}
    	
    	if (bAdapter.isEnabled()) {
    		btnBluetooth.setText(R.string.DesactivarBluetooth);
    		btnBuscarDispositiu.setEnabled(true);
    		if (servei == null) {
				servei = new BluetoothService(this, handler, bAdapter);
			}
			else {
				servei.finalitzarServei();
				servei.iniciarServei();
			}
    	}
    	else {
    		btnBluetooth.setText(R.string.ActivarBluetooth);
    		btnBuscarDispositiu.setEnabled(false);
    	}
    	
    }
    
    private void registrarEventosBluetooth() {
		// registrem el BroadcastReceiver que instanciem previament per detectar
		// els diferents canvis que volem rebre
    	
    	btnEnviar.setOnClickListener(this);
    	btnBluetooth.setOnClickListener(this);
    	btnBuscarDispositiu.setOnClickListener(this);
    	lvDispositius.setOnItemClickListener(this);
    	
		IntentFilter filtre = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
		filtre.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		filtre.addAction(BluetoothDevice.ACTION_FOUND);
		filtre.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		
		this.registerReceiver(bReceiver, filtre);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			// codi executat quan s'apreta el boto d'enviar un missatge
			case R.id.btnEnviar:
			{
				if (servei != null) {
					//TODO: canviar per textinput al main
					servei.enviar("hello world".getBytes());
				}
				break;
			}
		
			// codi executat quan s'apreta el boto que s'encarrega d'activar o desactivar
			// el bluetooth
			case R.id.btnBluetooth:
			{
				// 1. comprovar si el bluetooth esta activat o desactivat
				// 2. codificar l'activacio/desactivacio del bluetooth
				
				// si esta activat => el desactivo
				if (bAdapter.isEnabled()) {
					bAdapter.disable();
				}
				
				else {
					// llancem l'intent que mostrara l'interficie d'activar el bluetooth
					// la resposta d'aquest es gestionara en onActivityResult
					Intent enableBtnIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
					startActivityForResult(enableBtnIntent, REQUEST_ENABLE_BT);
				}
				break;
			}
			
			// codi executat quan s'apreta el boto que s'encarrega d'iniciar la cerca de dispositius
			case R.id.btnBuscarDispositiu:
			{
				if (arrayDevices != null) {
					arrayDevices.clear();
				}
				
				// comprovem si existeix un descobriment en curs. Si es que si, l'acabem
				if (bAdapter.isDiscovering()){
					bAdapter.cancelDiscovery();
				}
				
				// iniciem la cerca de dispositius i mostrem el missatge de que el proces ha començat
				if (bAdapter.startDiscovery()) {
					Toast.makeText(this, "Iniciant la cerca de dispositius bluetooth", Toast.LENGTH_SHORT).show();
				}
				else {
					Toast.makeText(this, "Error a l'iniciar la cerca de dispositius bluetooth", Toast.LENGTH_SHORT).show();
				}
				break;
			}
		}	
	}
	
	/**
	 * Handler de l'event desncadenat al retornar d'una activitat. En aquest cas, s'utilitza
	 * per comprovar el valor de retorn al llançar l'activitat que activara el bluetooth.
	 * Si l'usuari accepta, resultCode sera RESULT_OK
	 * Si no accepta, resultCode sera RESULT_CANCELED
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
			case REQUEST_ENABLE_BT:
			{
				btnBluetooth.setText(R.string.DesactivarBluetooth);
				btnBuscarDispositiu.setEnabled(true);
				if (servei == null) {
					servei = new BluetoothService(this, handler, bAdapter);
				}
				else {
					servei.finalitzarServei();
					servei.iniciarServei();
				}
				break;
			}
			default:
				break;	
		}
	}

	
	@Override
	protected void onDestroy() {
		// a mes de fer la destruccio de l'activitat, eliminem
		// el registre del BroadcastReceiver
		super.onDestroy();
		this.unregisterReceiver(bReceiver);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		// El ListView te un adaptador de tipo BluetoothDeviceArrayAdapter.
		// Invoquem el metodo getItem() de l'adaptador per rebre el dispositiu bluetooth
		// i fer la connexio
		BluetoothDevice dispositivo = (BluetoothDevice)lvDispositius.getAdapter().getItem(position);
		
		AlertDialog dialog = crearDialegConnexio(getString(R.string.Connectar), 
				getString(R.string.MsgConfirmarConnexio) + " " + dispositivo.getName() + "?", 
				dispositivo.getAddress());
		
		dialog.show();
		
	}

	private AlertDialog crearDialegConnexio(String titol, String missatge, final String direccio) {
		// Instanciamos un nuevo AlertDialog Builder y le asociamos titulo y mensaje
    	AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setTitle(titol);
		alertDialogBuilder.setMessage(missatge);

		// Creamos un nuevo OnClickListener para el boton OK que realice la conexion
		DialogInterface.OnClickListener listenerOk = new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				solicitarConnexio(direccio);
			}
		};
		
		// Creamos un nuevo OnClickListener para el boton Cancelar
		DialogInterface.OnClickListener listenerCancelar = new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				return;
			}
		};
		
		// Asignamos los botones positivo y negativo a sus respectivos listeners 
		alertDialogBuilder.setPositiveButton(R.string.Connectar, listenerOk);
		alertDialogBuilder.setNegativeButton(R.string.Cancelar, listenerCancelar);
		
		return alertDialogBuilder.create();
	}

    public void solicitarConnexio(String direccio)
    {
    	Toast.makeText(this, "Connectant amb " + direccio, Toast.LENGTH_LONG).show();
    	if(servei != null)
    	{
    		BluetoothDevice dispositiuRemot = bAdapter.getRemoteDevice(direccio);
    		servei.solicitarConnexio(dispositiuRemot);
    		Toast.makeText(this, "connexio inicialitzada", Toast.LENGTH_SHORT).show();

    		//servei.enviar("t'estimo!".getBytes());
    	}
    	else {
    		Toast.makeText(this, "error", Toast.LENGTH_SHORT).show();
    	}
    }

	
}
