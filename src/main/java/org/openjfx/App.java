package org.openjfx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

import com.opencsv.CSVWriter;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import javax.bluetooth.*;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.Connector;

import java.text.SimpleDateFormat;

/**
 * JavaFX App
 */
public class App extends Application {
    private static ArrayList<String> btDeviceNames = new ArrayList<>();
    private static boolean reading = true;
    private static boolean scanFinished;
    private static Button connectBTButton;
    private static Button refreshBTButton;
    private static ComboBox<String> bTList = new ComboBox<>();
    private static CSVWriter csvWriter;
    private static GridPane gridPane;
    private static GridPane lGridPane;
    private static HashMap<String, String> btDevices = new HashMap<>();
    private static InputStream is;
    private static LineChart<Number, Number> lineChart;
    private static long startTime;
    private static NumberAxis xAxis;
    private static NumberAxis yAxis;
    private static OutputStream os;
    private static Rectangle2D screenBounds;
    private static Scene scene;
    private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM_dd_yyyy_HH_mm_ss");
    private static StreamConnection c;
    private static String address;
    private static String btDeviceAddress;
    private static String btDeviceName;
    private static String fileName;
    private static String selectedDevice;
    private static String strToProcess;
    private static String[] header = {"Time", "Strain Gauge 1", "Strain Gauge 2", "Temperature (C)", "Acceleration (m/s^2)"};private static Thread connectThread;
    private static Thread getBTThread;
    private static VBox lVBox;
    private static VBox rVBox;
    private static XYChart.Series<Number, Number> accel;
    private static XYChart.Series<Number, Number> sg1;
    private static XYChart.Series<Number, Number> sg2;
    private static XYChart.Series<Number, Number> temp;

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) throws IOException {

        // create thread to get available bluetooth devices and add them to the dropdown
        getBTThread = new Thread(() -> {
            try {
                getAvailableBluetooth();
                // System.out.println(btDevices);
                btDeviceNames.clear();
                for(Map.Entry<String, String> entry : btDevices.entrySet()) {
                    btDeviceNames.add(entry.getKey());
                }
                // System.out.println(btDeviceNames);
                bTList.getItems().clear();
                bTList.getItems().addAll(btDeviceNames);
            } catch (IOException e) {
                e.printStackTrace();
            }
            refreshBTButton.setDisable(false);
        });

        // start the thread to get available bluetooth devices and add them to the dropdown
        getBTThread.start();

        // set screen bounds to the size of the screen
        screenBounds = Screen.getPrimary().getVisualBounds();
        stage.setMaxWidth(screenBounds.getWidth());
        stage.setMaxHeight(screenBounds.getHeight());

        // create the main grid pane
        gridPane = new GridPane();
        gridPane.setGridLinesVisible(true);
        gridPane.setPadding(new Insets(10, 10, 10, 10));
        scene = new Scene(gridPane, 640, 480);

        // create the left grid pane
        lGridPane = new GridPane();
        lGridPane.setPadding(new Insets(10));
        lGridPane.setHgap(10);
        lGridPane.setVgap(10);

        // create a vertical box for the left and right panes
        lVBox = new VBox();
        lVBox.setPrefWidth(screenBounds.getWidth());
        lVBox.setPrefHeight(screenBounds.getHeight());
        lVBox.isResizable();
        rVBox = new VBox();
        rVBox.setPrefWidth(screenBounds.getWidth());
        rVBox.setPrefHeight(screenBounds.getHeight());
        rVBox.isResizable();

        // create a line chart for the right pane
        xAxis = new NumberAxis();
        yAxis = new NumberAxis();
        lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setPrefHeight(rVBox.getPrefHeight());

        // create series to add to the chart
        sg1 = new XYChart.Series<>();
        sg1.setName("Strain Gauge 1");
        sg2 = new XYChart.Series<>();
        sg2.setName("Strain Gauge 2");
        temp = new XYChart.Series<>();
        temp.setName("Temperature (C)");
        accel = new XYChart.Series<>();
        accel.setName("Acceleration (m/s^2)");

        // add a button to connect to bluetooth
        connectBTButton = new Button("Connect Bluetooth");
        connectBTButton.setDisable(true);
        connectBTButton.setPrefWidth(100);
        connectBTButton.setText("Connect");
        connectBTButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                if(connectBTButton.getText().equals("Connect")) {
                    refreshBTButton.setDisable(true);
                    fileName = "data_" + simpleDateFormat.format(new Date()) + ".csv";
                    try {
                        csvWriter = new CSVWriter(new FileWriter(fileName));
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                    csvWriter.writeNext(header);
                    selectedDevice = bTList.getValue();
                    address = btDevices.get(selectedDevice);
                    startTime = System.currentTimeMillis();
                    reading = true;
                    connectThread = new Thread(() -> {
                        try {
                            connect(address);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    connectThread.start();
                    connectBTButton.setText("Disconnect");
                    // System.out.println("Connect Bluetooth Button Clicked");
                } else {
                    refreshBTButton.setDisable(false);
                    reading = false;
                    sg1.getData().clear();
                    sg2.getData().clear();
                    temp.getData().clear();
                    accel.getData().clear();
                    try {
                        csvWriter.flush();
                        csvWriter.close();
                        os.close();
                        is.close();
                        c.close();
                        connectThread.join();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    connectBTButton.setText("Connect");
                    // System.out.println("Disconnect Bluetooth Button Clicked");
                }
            }
        });

        // create a list of bluetooth devices
        bTList.setPrefWidth(210);
        bTList.getItems().addAll(btDeviceNames);
        bTList.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                // System.out.println("Selected: " + bTList.getValue());
                connectBTButton.setDisable(false);
                getBTThread.interrupt();
                connectBTButton.setText("Connect");
            }
        });

        // refresh list of bluetooth devices
        refreshBTButton = new Button("Refresh Bluetooth List");
        refreshBTButton.setDisable(true);
        refreshBTButton.setText("Refresh");
        refreshBTButton.setPrefWidth(100);
        refreshBTButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                refreshBTButton.setDisable(true);
                getBTThread = new Thread(() -> {
                    try {
                        getAvailableBluetooth();
                        // System.out.println(btDevices);
                        btDeviceNames.clear();
                        for(Map.Entry<String, String> entry : btDevices.entrySet()) {
                            btDeviceNames.add(entry.getKey());
                        }
                        // System.out.println(btDeviceNames);
                        Platform.runLater(() -> {
                            bTList.getItems().clear();
                            bTList.getItems().addAll(btDeviceNames);
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    refreshBTButton.setDisable(false);
                });
                // System.out.println("Refresh Bluetooth List Button Clicked");
                getBTThread.start();
            }
        });

        // add all elements to the grid pane
        gridPane.add(lVBox, 0, 0, 1, 1);
            lVBox.getChildren().add(lGridPane);
                lGridPane.add(bTList, 0, 0, 2, 1);
                lGridPane.add(refreshBTButton, 0, 1, 1, 1);
                lGridPane.add(connectBTButton, 1, 1, 1, 1);
        gridPane.add(rVBox, 1, 0, 1, 1);
            rVBox.getChildren().add(lineChart);
                lineChart.getData().add(sg1);
                lineChart.getData().add(sg2);
                lineChart.getData().add(temp);
                lineChart.getData().add(accel);

        // set title of the stage
        stage.setTitle("Data Collector");

        // show the stage
        stage.setScene(scene);
        stage.show();

        // close the program
        stage.setOnCloseRequest((ae) -> {
            // commented stuff probably not necessary
            // reading = false;
            // sg1.getData().clear();
            // sg2.getData().clear();
            // temp.getData().clear();
            // accel.getData().clear();
            // try {
            //     if(!(csvWriter == null)) {
            //         csvWriter.flush();
            //         csvWriter.close();
            //     }
            //     if (os != null) os.close();
            //     if (is != null) is.close();
            //     if (c != null) c.close();
            //     if (connectThread != null) connectThread.join();
            // } catch (Exception e) {
            //     e.printStackTrace();
            // }
            Platform.exit();
            System.exit(0);
        });
    }

    // searches for the bluetooth devices
    private void getAvailableBluetooth() throws IOException {
        btDevices.clear();
        scanFinished = false;
        LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, new DiscoveryListener() {
            @Override
            public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
                try {
                    String name = btDevice.getFriendlyName(false);
                    // System.out.println("Device found: " + name);
                    btDeviceName = name;
                    btDeviceAddress = "btspp://" + btDevice.getBluetoothAddress() + ":1;authenticate=false;encrypt=false;master=false";
                    // note: the line above is a kind of janky solution, the alternative would probably use the UUID (which is a lot more code that I don't currently have time for)
                    // System.out.println("Device address: " + btDeviceAddress);
                    btDevices.put(btDeviceName, btDeviceAddress);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void inquiryCompleted(int discType) {
                scanFinished = true;
            }
            @Override
            public void serviceSearchCompleted(int transID, int respCode) {
            }
            @Override
            public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
            }
        });
        while (!scanFinished) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    // connects to the bluetooth device and starts reading data from it
    private static void connect(String address) throws IOException {
        try {
            c = (StreamConnection) Connector.open(address);
            os = c.openOutputStream();
            LocalDevice ld = LocalDevice.getLocalDevice();
        } catch (IOException e) {
            e.printStackTrace();
        }
        RemoteDevice rd;
        try {
            rd = RemoteDevice.getRemoteDevice(c);
            is = c.openInputStream();
        } catch(IOException e) {
            e.printStackTrace();
        }
        strToProcess = "";
        while(reading) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            try {
                bytesRead = is.read(buffer);
                String m = new String(buffer, 0, bytesRead);
                strToProcess += m;
                processData();
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // processes the data received from the bluetooth device
    public static void processData() throws IOException {
        if(strToProcess.contains(";")) {
            String text = strToProcess.substring(0, strToProcess.indexOf(";"));
            strToProcess = strToProcess.substring(strToProcess.indexOf(";") + 1);
            List<String> data = Arrays.stream(text.split("[\\p{Punct}&&[^.]]"))
                                        .filter(s -> s.split("\\p{Alpha}").length == 1)
                                        .collect(Collectors.toList());
            // System.out.println(data);
            if(data.size() == 4) {
                double sg1Value = Double.parseDouble(data.get(0));
                double sg2Value = Double.parseDouble(data.get(1));
                double tempValue = Double.parseDouble(data.get(2));
                double accelValue = Double.parseDouble(data.get(3));
                double currentTime = (System.currentTimeMillis() - startTime) / 1000.0;
                Platform.runLater(() -> {
                    sg1.getData().add(new XYChart.Data<>(currentTime, sg1Value));
                    sg2.getData().add(new XYChart.Data<>(currentTime, sg2Value));
                    temp.getData().add(new XYChart.Data<>(currentTime, tempValue));
                    accel.getData().add(new XYChart.Data<>(currentTime, accelValue));
                });
                csvWriter.writeNext(new String[]{((long)(currentTime * 100) / 100.0) + "", sg1Value + "", sg2Value + "", tempValue + "", accelValue + ""}, false);
                csvWriter.flush();
            }
        }
    }
}