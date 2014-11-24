package com.example.myfirstapp;

import java.util.List;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class BluetoothDeviceArrayAdapter extends ArrayAdapter<Object> {
	private List<BluetoothDevice> deviceList; // contindra el llistat de dispositius
	private Context context; // contindra el contexte actiu
	
	public BluetoothDeviceArrayAdapter(Context context, int textViewResourceId, List<BluetoothDevice> objects) {
		// invoquem el constructor base
		super(context, textViewResourceId);
		
		// assignem els parametres als atributs
		this.deviceList = objects;
		this.context = context;
	}
	
	@Override
	public int getCount() {
		if(deviceList != null) {
			return deviceList.size();
		}
		else
			return 0;
	}
	
	@Override
	public Object getItem(int position) {
		return (deviceList == null ? null : deviceList.get(position));
	}
	
	@SuppressLint("ViewHolder")
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if((deviceList == null) || (context == null)) {
			return null;
		}
		
		// utilitzem un layoutinflater per crear les vistes
		LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		// creem una vista a partir de la simple_list_item_2, que conte dos TextView
		// el primer (text1) l'utilitzem pel nom, i el segon (text2) per la direccio del dispositiu
		View element = inflater.inflate(android.R.layout.simple_list_item_2, parent, false);
		
		// referenciem els TextView
		TextView tvNom = (TextView)element.findViewById(android.R.id.text1);
		TextView tvDireccio = (TextView)element.findViewById(android.R.id.text2);
		
		// obtenim el dispositiu de l'array i obtenim el seu nom i direccio, associantlo als dos TextView de l'element
		BluetoothDevice dispositiu = (BluetoothDevice)getItem(position);
		if(dispositiu != null) {
			tvNom.setText(dispositiu.getName());
			tvDireccio.setText(dispositiu.getAddress());
		}
		else {
			tvNom.setText("ERROR");
		}
		
		// retornem l'element amb els dos TextView complimentats
		return element;
	}
}
