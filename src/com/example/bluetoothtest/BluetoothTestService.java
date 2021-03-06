package com.example.bluetoothtest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Random;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;

import omronspp.OmronBaseClass;

public class BluetoothTestService {
	// Debugging
    private static final String TAG = "BluetoothTestService";
    private static final boolean D = true;
    
 // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothTestSecure";
    private static final String NAME_INSECURE = "BluetoothTestInsecure";

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
        UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID MY_UUID_INSECURE =
        UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    
 // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    
    
    /**
     * Constructor. Prepares a new BluetoothTest session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothTestService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }
    
    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(BluetoothTest.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }
    
    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        setState(STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(true);
            //mSecureAcceptThread.start();
        }
        if (mInsecureAcceptThread == null) {
            //mInsecureAcceptThread = new AcceptThread(false);
            //mInsecureAcceptThread.start();
        }
    }
    
    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        if (mConnectThread.running) mConnectThread.start();//only run the thread if initialization is successful - a Bluetooth socket can be created
        setState(STATE_CONNECTING);
    }
    
    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device) {
        if (D) Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            //mSecureAcceptThread.cancel();
            //mSecureAcceptThread = null;
        }
        if (mInsecureAcceptThread != null) {
            //mInsecureAcceptThread.cancel();
            //mInsecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(BluetoothTest.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothTest.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            //mSecureAcceptThread.cancel();
            //mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            //mInsecureAcceptThread.cancel();
            //mInsecureAcceptThread = null;
        }
        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }
    
    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothTest.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothTest.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothTestService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothTest.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothTest.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothTestService.this.start();
    }
    
    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure":"Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, MY_UUID_SECURE);
                	
                	//I need to construct mmDevice object
                	/*BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                	BluetoothDevice mmDevice = mBluetoothAdapter.getRemoteDevice("00:22:58:35:C2:5E");
                    boolean temp = mmDevice.fetchUuidsWithSdp();
                	UUID uuid = null;
                	if( temp ){
                	uuid = mmDevice.getUuids()[0].getUuid();
                	}
                	Log.d(TAG, "uuid is " + uuid);
                	tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, uuid);*/
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
                    /*boolean temp = mmDevice.fetchUuidsWithSdp();
                	UUID uuid = null;
                	if( temp ){
                	uuid = mmDevice.getUuids()[0].getUuid();
                	}
                	tmp = device.createRfcommSocketToServiceRecord(uuid);*/
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (D) Log.d(TAG, "Socket Type: " + mSocketType +
                    "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothTestService.this) {
                        switch (mState) {
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                            connected(socket, socket.getRemoteDevice());
                            break;
                        case STATE_NONE:
                        case STATE_CONNECTED:
                            // Either not ready or already connected. Terminate new socket.
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Could not close unwanted socket", e);
                            }
                            break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

        }

        public void cancel() {
            if (D) Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }
    
    
    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket = null;
        private final BluetoothDevice mmDevice;
        //private String mSocketType;
        private volatile boolean running = false;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            
        	mmDevice = device;
            BluetoothSocket tmp = null;

            //===============================================================================================/
            //Method #2 - Used in BluetoothChat example but "Service discovery failed" all the freaking times
            try {
             	//hardcoding UUID does not work for Android 4.2.2 API 17, some even says all Jelly Bean devices - http://stackoverflow.com/a/13689775/541624
                
                tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                //if (tmp==null) tmp = device.createRfcommSocketToServiceRecord(MY_UUID);;
                
            } catch (IOException e) {
            	if (D) Log.e(TAG, "could not create the socket", e);
            }
            
            /*if (tmp==null) {
            	//Method #3 -
                //1. "Connection refused" Android 4.0.3 API 15
                //2. works for Android 4.2.2 API 17
                //3. works for Android 4.1.2 API 16 without pairing - although pairing is ignored, the next attempt to connect from the BPM brings up pairing confirmation popup ==> it remembers the phone
                //3.1. but phone should already remembers/ be remembered after 1st pairing right? ==> BPM or phone's memory is mysteriously cleared some how
                //3.2. when connection is successful, UUID is the default "00001101-0000-1000-8000-00805f9b34fb" anyway so why it fails in the 1st place?
                //3.3. After one failed attempt ("Service discovery failed") I tried again and succeeded
                try {
                	UUID mUUID = MY_UUID; 
                	if (device.getUuids()!=null) {
                		//when phone is not paired to Omron device, device.getUuids() returns null
                		//hardcoding UUID does not work for Android 4.2.2 API 17, some even says all Jelly Bean devices - http://stackoverflow.com/a/13689775/541624
                		ParcelUuid[] phoneUuids = device.getUuids();
                		if (phoneUuids.length>0) {
                			for (int i=0; i<phoneUuids.length; i++) {
                		
    	                		mUUID = phoneUuids[i].getUuid();
    	                		if (D) Log.i(TAG, "the stored (?) device UUID is "+mUUID);
                			}
                		}
                	}
                	
                    if (secure) {
                    	tmp = device.createRfcommSocketToServiceRecord(mUUID);
                    }
                    else {
                    	tmp = device.createInsecureRfcommSocketToServiceRecord(mUUID);
                    }
                } catch (IOException e) {
                	Log.e(TAG, "Socket Type: " + "create() failed", e);
                } catch (Exception e) {
                	Log.e(TAG, "what freaking exception is happening?", e);
                }
            }*/
            
            /*if (tmp==null) {
            	//Method #1 - "permission denied" for 4.0.3 API 15
                //works for 4.2.2 API 17
                //does not work for 4.1.2 API 16 "permission denied"
                try {
                	Method m = device.getClass().getMethod("createRfcommSocket", new Class[] {int.class});
                    tmp = (BluetoothSocket) m.invoke(device, 1);
                } catch (Exception e) {
                	if (D) Log.e(TAG, "could not create the socket", e);
                }
            }*/
            
            if (tmp!=null) {
            	if (D) Log.i(TAG, "Awesome, a Bluetooth socket successfully initialised"); 
            	mmSocket = tmp;
            	running = true;
            } else {
            	//finish();
            }
            
        }

        public void run() {
        	//while (running) {
        		Log.i(TAG, "BEGIN mConnectThread");
                //setName("ConnectThread" + mSocketType);

                // Always cancel discovery because it will slow down a connection
                mAdapter.cancelDiscovery();

                // Make a connection to the BluetoothSocket
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    mmSocket.connect();
                } catch (IOException e) {
                	Log.e(TAG, "failed to connect to the device", e);
                    // Close the socket
                    try {
                        mmSocket.close();
                    } catch (IOException e2) {
                        Log.e(TAG, "unable to close() " +
                                " socket during connection failure", e2);
                    }
                    connectionFailed();
                    return;
                }

                // Reset the ConnectThread because we're done???
                //synchronized (BluetoothTestService.this) {
                //    mConnectThread = null;
                //}

                // Start the connected thread
                connected(mmSocket, mmDevice);
                
                // Reset the ConnectThread because we're done???
                //synchronized (BluetoothTestService.this) {
                //    mConnectThread = null;
                //}
                running = false;
                this.interrupt();
                mConnectThread = null;
        	//}
        }
        
        //why would I need to close the socket here?
        public void cancel() {
            /*if (mmSocket!=null) {
            	try {
                    
                    mmSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, "close() of connect " + " socket failed", e);
                }
            }*/
        	//Stopping the thread would be enough, shouldn't close the socket, it is not for this thread to decide
        	running = false;
        	this.interrupt();
        	//return;
            
        }
        
    }
    
    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread: ");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
        	if (mConnectThread!=null) {
        		mConnectThread.interrupt();
        		//mConnectThread.cancel();
        		mConnectThread = null;//is this the proper way to destroy/ cancel the connectthread? No
        		
        	}
        	Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int len;
            boolean flag = false;
            String response = "";

            // Keep listening to the InputStream while connected
            //while (true) {
                try {
                    // Read from the InputStream
                	//if (mmInStream.available()>0) {
                		len = mmInStream.read(buffer);
                		
                		// Send the obtained bytes to the UI Activity
                        //mHandler.obtainMessage(BluetoothTest.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                		flag = OmronBaseClass.checkReady(buffer, len);
                	//}
                	
                	

                    
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    // Start the service over to restart listening mode
                    BluetoothTestService.this.start();
                    //break;
                }
                
            if (flag) {  
	            //}
	            try {
		            //Send TOK command
		            
		        	if (D) Log.w(TAG, "Sending the TOK command"); 
		    		sendCmd(OmronBaseClass.cmdTOK());
		    		//if (mmInStream.available()>0) {
		    		Thread.sleep(500);
		        		len = mmInStream.read(buffer);
		        		response = OmronBaseClass.handleCmdTOK(buffer, len);
		        		Log.w(TAG, response);
		        		if (response.equals("NO")) {
		        			if (D) Log.w(TAG, "Try ending the TOK command once again");
		        			Thread.sleep(3500);
		        			sendCmd(OmronBaseClass.cmdTOK());
				    		//if (mmInStream.available()>0) {
				    		Thread.sleep(500);
				    		len = mmInStream.read(buffer);
			        		response = OmronBaseClass.handleCmdTOK(buffer, len);
			        		Log.w(TAG, response);
		        		}
		    		//}
	            } catch (IOException e) {
	            	if (D) Log.e(TAG, "y quit so early?", e); 
	            } catch (InterruptedException e) {
	            	if (D) Log.e(TAG, "connected thread was interrupted", e); 
	            }
            }
            
            //Stop the thread after 10 seconds just for testing
            /*try {
    			this.join(5000);
    		} catch (InterruptedException e) {
    			// TODO Auto-generated catch block
    			if (D) Log.e(TAG, "could not join thread ", e); 
    		}
            this.cancel();*/
        }
        
        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(BluetoothTest.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
        	byte[] buffer = new byte[1024];
        	int len = 0;
            try {
            	//Send TOK command
            	if (D) Log.w(TAG, "Sending the TOK comand"); 
        		sendCmd(OmronBaseClass.cmdTOK());
        		len = mmInStream.read(buffer);
        		String response = OmronBaseClass.handleCmdTOK(buffer, len);
        		Log.w(TAG, response);
        		
        		if (D) Log.w(TAG, "closing the bluetooth socket"); 
                //mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
            
            //Shouldn't I kill this thread as well?
            //BluetoothTestService.this.start();
            //this.interrupt();
        }
        
        private void sendCmd(String cmd){
        	
        	try {
        		byte[] byteCmd = cmd.getBytes();
        		//write(byteCmd);
        		mmOutStream.write(byteCmd);
        		//Thread.sleep(500);
        	} catch (IOException e){
        		e.printStackTrace();
        	} catch (Exception e) {
        		if (D) Log.e(TAG, "could notsend the command, something went wrong", e);
        	}
    		/*try {
				sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				Log.d(TAG, "Some odd exception happens while sending commands to the BPM", e);
			}*/
        }
    }

}
