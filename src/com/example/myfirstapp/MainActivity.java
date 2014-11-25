package com.example.myfirstapp;

import java.util.ArrayList;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements android.view.View.OnClickListener
{
	// boto de bluetooth
	private Button btnBluetooth;
	
	// boto de buscar dispositius
	private Button btnBuscarDispositiu;
	
	// adaptador bluetooth del telefon
	private BluetoothAdapter bAdapter;
	
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
						((Button)findViewById(R.id.btnBluetooth)).setText(R.string.ActivarBluetooth);
						((Button)findViewById(R.id.btnBuscarDispositiu)).setEnabled(false);
						buidarLlistaDispositius();
						break;
	
					// Ences
					case BluetoothAdapter.STATE_ON:
						// canviem el text del boto
						((Button)findViewById(R.id.btnBluetooth)).setText(R.string.DesactivarBluetooth);
						
						// llancem intent de solicitud de visibilitat bluetooth al qual afegim un
						// parell clau-valor que indicara la duracio de l'estat, en aquest cas 120 segons
						Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
						discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
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
				if (arrayDevices != null) {
					int ndisp = arrayDevices.size();
					if (ndisp > 0) {
						Toast.makeText(getBaseContext(), "Fi de la cerca, he trobat: "+
								(ndisp == 1 ? "1 dispositiu" : ndisp + " dispositius"),
								Toast.LENGTH_SHORT).show();
					}
					else { //TODO: missatges del toast al fitxer strings.xml?
						Toast.makeText(getBaseContext(), "Fi de la cerca, no he trobat cap dispositiu", Toast.LENGTH_SHORT).show();
					}
				}				
				else {
					Toast.makeText(getBaseContext(), "Fi de la cerca, no he trobat cap dispositiu", Toast.LENGTH_SHORT).show();
				}
				
			}
			else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
				buidarLlistaDispositius();
				Toast.makeText(getBaseContext(), "Començant cerca dispositius", Toast.LENGTH_SHORT).show();
			}
		}

		private void buidarLlistaDispositius() {
			// TODO Auto-generated method stub
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
					missatge = "Enviant missatge: " + missatge;
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
        
        btnBluetooth = (Button)findViewById(R.id.btnBluetooth);
        btnBuscarDispositiu = (Button)findViewById(R.id.btnBuscarDispositiu);
        btnBuscarDispositiu.setText(R.string.DescobrirDispositius);
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
    	}
    	else {
    		btnBluetooth.setText(R.string.ActivarBluetooth);
    		btnBuscarDispositiu.setEnabled(false);
    	}
    }
    
    private void registrarEventosBluetooth() {
		// registrem el BroadcastReceiver que instanciem previament per detectar
		// els diferents canvis que volem rebre
    	btnBluetooth.setOnClickListener(this);
    	btnBuscarDispositiu.setOnClickListener(this);

		IntentFilter filtre = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
		this.registerReceiver(bReceiver, filtre);
		
		IntentFilter filtre2 = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		filtre2.addAction(BluetoothDevice.ACTION_FOUND);
		filtre2.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		
		this.registerReceiver(bReceiver, filtre2);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
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
	
}
