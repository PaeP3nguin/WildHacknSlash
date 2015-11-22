package com.paep3nguin.wildhacks;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.devpaul.bluetoothutillib.SimpleBluetooth;
import com.devpaul.bluetoothutillib.utils.SimpleBluetoothListener;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements SimpleBluetoothListener, SensorEventListener {

    /* Game parameters */
    private static final int SENSITIVITY = 5;
    private static final int REACTION_TIME = 400;
    private static final int MAX_HEALTH = 10;

    /* Bluetooth constants */
    private static final int YOU_WIN = 1;
    private static final int PLAY_AGAIN = 2;
    private static final int HACK_VERTICAL = 10;
    private static final int HACK_LEFT = 20;
    private static final int HACK_RIGHT = 30;
    private static final int STAB = 40;
    private static final String SERVER_MAC = "F8:E0:79:43:B3:B0";
    private static final String OLD_MAC = "F8:E0:79:6E:EB:68";

    /* Sound IDs */
    private static final int ATTACK1 = 0;
    private static final int ATTACK2 = 1;
    private static final int BLOCK1 = 2;
    private static final int BLOCK2 = 3;
    private static final int BLOCK3 = 4;
    private static final int HIT_SOUND = 5;
    private static final int LOSS_SOUND = 6;
    private static final int WIN_SOUND = 7;

    private Dialog dialog;
    View whiteBar;
    View redBar;

    @Bind(R.id.toolbar) Toolbar toolbar;

    private SimpleBluetooth simpleBluetooth;
    private Handler handler;
    private Vibrator v;
    private SoundPool soundPool;
    private ArrayList<Integer> soundIDs;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private LinkedList<Float[]> lastAccels;
    private Random random;
    private BluetoothDevice bluetoothDevice;

    /* Game status */
    private int health = MAX_HEALTH;
    private AtomicInteger action;
    private boolean in_game = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        dialog = new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog);
        whiteBar = ButterKnife.findById(dialog, R.id.red_bar);
        redBar = ButterKnife.findById(dialog, R.id.white_bar);

        setSupportActionBar(toolbar);

        handler = new Handler();
        v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
        soundIDs = new ArrayList<>();
        soundIDs.add(soundPool.load(this, R.raw.knife_stab, 1));
        soundIDs.add(soundPool.load(this, R.raw.swipe, 1));
        soundIDs.add(soundPool.load(this, R.raw.swords_hit_1, 1));
        soundIDs.add(soundPool.load(this, R.raw.swords_hit_2, 1));
        soundIDs.add(soundPool.load(this, R.raw.swords_hit_3, 1));
        soundIDs.add(soundPool.load(this, R.raw.wilhelm, 1));
        soundIDs.add(soundPool.load(this, R.raw.fatality, 1));
        soundIDs.add(soundPool.load(this, R.raw.triumph, 1));

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        simpleBluetooth = new SimpleBluetooth(this, this);
        simpleBluetooth.initializeSimpleBluetooth();
        simpleBluetooth.setSimpleBluetoothListener(this);

        lastAccels = new LinkedList<>();

        action = new AtomicInteger(0);
        random = new Random();
    }

    @Override
    protected void onResume() {
        if (in_game) {
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        }

        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        simpleBluetooth.endSimpleBluetooth();

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @OnClick(R.id.throw_down)
    public void throwDown() {
        if (bluetoothDevice != null) {
            startGame();
            simpleBluetooth.sendData(PLAY_AGAIN);
            return;
        }

        simpleBluetooth.initializeSimpleBluetooth();
        simpleBluetooth.createBluetoothServerConnection();
    }

    @OnClick(R.id.fight_button)
    public void fight() {
        if (bluetoothDevice != null) {
            startGame();
            simpleBluetooth.sendData(PLAY_AGAIN);
            return;
        }

        simpleBluetooth.initializeSimpleBluetooth();
        Log.i("WildHacks", "My MAC is: " + BluetoothAdapter.getDefaultAdapter().getAddress());
        if (BluetoothAdapter.getDefaultAdapter().getAddress().equals(OLD_MAC)) {
            simpleBluetooth.connectToBluetoothServer(SERVER_MAC);
        } else {
            simpleBluetooth.connectToBluetoothServer(OLD_MAC);
        }
    }

    public void endDuel(boolean win) {
        if (win) {
            playSound(WIN_SOUND);
        } else {
            playSound(LOSS_SOUND);
            simpleBluetooth.sendData(YOU_WIN);
        }

        endGame();
    }

    @Override
    public void onBluetoothDataReceived(byte[] bytes, String s) {
        if (bytes.length > 1) {
            Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
        } else {
            switch (bytes[0]) {
                case YOU_WIN:
                    endDuel(true);
                    break;
                case PLAY_AGAIN:
                    startGame();
                    break;
                case STAB:
                    action.set(STAB);

                    Log.i("Action", "Stab incoming!");

                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (action.compareAndSet(STAB, 0)) {
                                takeDamage();
                                Log.i("Action", "Got stabbed ;(");
                            }
                        }
                    }, REACTION_TIME);
                    break;
            }
        }
    }

    @Override
    public void onDeviceConnected(BluetoothDevice bluetoothDevice) {
        Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show();
        this.bluetoothDevice = bluetoothDevice;
        startGame();
    }

    @Override
    public void onDeviceDisconnected(BluetoothDevice bluetoothDevice) {
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        bluetoothDevice = null;
        endGame();
    }

    public void startGame() {
        in_game = true;
        setHealth(MAX_HEALTH);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        dialog.show();
    }

    public void endGame() {
        in_game = false;
        mSensorManager.unregisterListener(this);
        dialog.dismiss();
    }

    @Override
    public void onDiscoveryStarted() {

    }

    @Override
    public void onDiscoveryFinished() {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == mAccelerometer) {

            Log.i("S", String.format("x: %3.3f, y: %3.3f, z: %3.3f", event.values[0], event.values[1], event.values[2]));

            int attack = 0;
            boolean cancel = false;

            if (event.values[1] >= 9.8 + SENSITIVITY && lastAccels.size() > 0 && lastAccels.getLast()[1] < 9.8 + 8) {
                for (Float[] val : lastAccels) {
                    if (val[1] < -10) {
                        Log.i("S", String.format("Bad stab stopped y: %3.3f", val[1]));
                        lastAccels.clear();
                        cancel = true;
                        break;
                    }
                }

                if (!cancel) {
                    attack = STAB;
                }
            }

            switch (action.get()) {
                case 0:
                    switch (attack) {
                        case STAB:
                            simpleBluetooth.sendData(STAB);
                            playSound(ATTACK1);
                            Log.i("Attack", "Stab");
                            lastAccels.clear();
                            break;
                    }
                    break;
                case STAB:
                    if (Math.abs(event.values[2]) >= 9.8 + SENSITIVITY || attack == STAB) {
                        action.set(0);
                        playSound(random.nextInt((BLOCK3 - BLOCK1) + 1) + BLOCK1);
                        Log.i("Action", "Stab blocked!");
                    }
                    break;
            }

            lastAccels.add(new Float[]{event.values[0], event.values[1], event.values[2]});

            if (lastAccels.size() > 10) {
                lastAccels.remove();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void playSound(int soundID) {
        soundPool.play(soundIDs.get(soundID), 1, 1, 0, 0, 1);
    }

    public void takeDamage() {
        setHealth(health - 1);
        v.vibrate(200);
        playSound(HIT_SOUND);
    }

    public void setHealth(int new_val) {
        health = new_val;
        LinearLayout.LayoutParams param1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT, new_val);
        whiteBar.setLayoutParams(param1);

        LinearLayout.LayoutParams param2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT, MAX_HEALTH - new_val);
        redBar.setLayoutParams(param2);

        if (health == 0) {
            endDuel(false);
        }
    }
}
