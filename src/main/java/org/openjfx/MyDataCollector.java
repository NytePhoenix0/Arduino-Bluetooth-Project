package org.openjfx;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

import javax.bluetooth.*;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ValueAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class MyDataCollector extends Application {

    @SuppressWarnings("rawtypes")
    private static ComboBox cbBluetooth;
    private static Button btRefresh, btConnect;
    private static Button btClear;
    private static Button btSend;
    private static TextArea textArea;
    private static Label lbBluetooth;
    private static ExecutorService executor;
    private static StreamConnection connection;
    private static InputStream input;
    private static boolean bKeepReading;
    private static String strWaitingforProcessing;
    private static int iTotalDataDisplayed = 0;
    private static int iTotalDataToSave = 0;
    private static LineChart<Number,Number> lineChart;
    @SuppressWarnings("rawtypes")
    private static Series tempSeries,accSeries,sg1Series,sg2Series;
    boolean scanFinished = false;
    RemoteDevice myBluetooth;
    String myBluetoothServiceURL;
    String myBluetoothDevice;
    private Task<String> taskScanBluetooth;
    private Task<String> taskConnectBT;
    final static int MAX_DISPLAY = 120;
    final static int MAX_SAVE = 1200;
    Map<String, String> BTNameURL;
    private static String unsavedRecords = "Temperature(C),Acceleration(m/s^2),Strain Gage 1, Strain Gage 2\r\n";
    private static String strDirectory = "";
    private static String startTime = "";
    private static OutputStream os;
    @SuppressWarnings({ "rawtypes", "unchecked", "exports" })
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Bluetooth Data Collector");
        BTNameURL= new HashMap<>();
        executor = Executors.newFixedThreadPool(10);
        taskScanBluetooth = new Task<String>() {
            @Override
            @SuppressWarnings("UseSpecificCatch")
            protected String call() throws InterruptedException {
                Platform.runLater(()->{ try {
                    scanAvailableBluetooth();
                    } catch (Exception ex) {
                    }
                });
                return "Done!";
            }
        };

        Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
        primaryStage.setMaxWidth(primaryScreenBounds.getWidth());
        primaryStage.setMaxHeight(primaryScreenBounds.getHeight());

        GridPane gridPane = new GridPane();
        gridPane.setGridLinesVisible(true);
        gridPane.setPadding(new Insets(10));

        Scene scene = new Scene(gridPane);

        VBox vBoxMain = new VBox();
        vBoxMain.setPrefWidth(primaryScreenBounds.getWidth()*0.6);
        vBoxMain.setPrefHeight(primaryScreenBounds.getHeight()*0.9);
        vBoxMain.setPadding(new Insets(20,10,20,10));
        vBoxMain.isResizable();

        VBox vBoxSide = new VBox();
        vBoxSide.setPrefWidth(primaryScreenBounds.getWidth()*0.4);
        vBoxSide.setPrefHeight(primaryScreenBounds.getHeight()*0.9);
        vBoxSide.setPadding(new Insets(20,10,20,10));

        GridPane leftGridPane = new GridPane();
        leftGridPane.setVgap(20);
        leftGridPane.setHgap(20);
        lbBluetooth = new Label("Searching for Bluetooth...");
        lbBluetooth.setFont(Font.font("Verdana", FontWeight.NORMAL, 20));
        cbBluetooth = new ComboBox();
        cbBluetooth.setPrefWidth(300);
        cbBluetooth.setCenterShape(true);
        cbBluetooth.setOnAction((EventHandler<ActionEvent>) new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) {
                myBluetoothDevice = (String) cbBluetooth.getValue();
                myBluetoothServiceURL = BTNameURL.get(myBluetoothDevice);
                if(myBluetoothServiceURL!=null && myBluetoothServiceURL.contains("btspp:")) {
                    btConnect.setDisable(false);
                } else {
                    btConnect.setDisable(true);
                }
            }
        });
        btRefresh = new Button("Refresh");
        btRefresh.setFont(Font.font("Verdana", FontWeight.BOLD, 20));
        btRefresh.setPrefWidth(200);
        btRefresh.setOnAction((EventHandler<ActionEvent>) new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent e) {
                    int num = cbBluetooth.getItems().toArray().length;
                    for(int i=0;i<num;i++) cbBluetooth.getItems().remove(0);
                    taskScanBluetooth.cancel();
                    taskScanBluetooth = null;
                    taskScanBluetooth = new Task<String>() {
                        @Override
                        @SuppressWarnings("UseSpecificCatch")
                        protected String call() throws InterruptedException {
                            BTNameURL.clear();
                            btRefresh.setDisable(true);
                            Platform.runLater(()->{
                                try {
                                    scanAvailableBluetooth();
                                } catch (Exception ex) {
                                }
                            });
                        return "Done!";
                        }
                    };
                    executor.submit(taskScanBluetooth);
            }
        });

        TextField tfDirectory = new TextField();
        tfDirectory.setFont(Font.font("Verdana", FontWeight.BOLD, 20));
        tfDirectory.setPrefWidth(1000);
        tfDirectory.setText(new File("src").getAbsolutePath());
        strDirectory = tfDirectory.getText();
        tfDirectory.setDisable(true);
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setInitialDirectory(new File("src"));
        Button btDirectory = new Button("Save Data To...");
        btDirectory.setOnAction(e -> {
            File selectedDirectory = directoryChooser.showDialog(primaryStage);
            tfDirectory.setText(selectedDirectory.getAbsolutePath());
            strDirectory = selectedDirectory.getAbsolutePath();
        });
        btDirectory.setPrefWidth(300);
        btDirectory.setFont(Font.font("Verdana", FontWeight.BOLD, 20));
        btConnect = new Button("Connect");
        btConnect.setFont(Font.font("Verdana", FontWeight.BOLD, 20));
        btConnect.setPrefWidth(300);
        btConnect.setOnAction((EventHandler<ActionEvent>) new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) {
                if("Connect".equals(btConnect.getText())) {
                    btConnect.setText("Disconnect");
                    bKeepReading = true;
                    if(taskConnectBT!=null) {
                        taskConnectBT.cancel();
                        taskConnectBT = null;
                    }
                    taskConnectBT = new Task<String>() {
                        @Override
                        @SuppressWarnings("UseSpecificCatch")
                        protected String call() throws InterruptedException, IOException {
                            bKeepReading = true;
                            try {
                                openConnection(myBluetoothServiceURL);
                            } catch (IOException | InterruptedException e) {
                            }
                            return "Done!";
                        }
                    };
                    executor.submit(taskConnectBT);
                } else {
                    btConnect.setText("Connect");
                    myBluetoothDevice = (String) cbBluetooth.getValue();
                    myBluetoothServiceURL = BTNameURL.get(myBluetoothDevice);
                    if(myBluetoothServiceURL!=null && myBluetoothServiceURL.contains("btspp:")) {
                        btConnect.setDisable(false);
                    } else {
                        btConnect.setDisable(true);
                    }
                    if(connection!=null) bKeepReading = false;
                }
            }
        });
        btConnect.setDisable(true);
        if(myBluetoothServiceURL!=null && myBluetoothServiceURL.contains("btspp:")) {
            btConnect.setDisable(false);
        } else {
            btConnect.setDisable(true);
        }
        btClear = new Button("Clear");
        btClear.setFont(Font.font("Verdana", FontWeight.BOLD, 20));
        btClear.setPrefWidth(300);
        btClear.setDisable(true);
        textArea = new TextArea();
        textArea.setWrapText(true);
        textArea.setPrefHeight(primaryScreenBounds.getHeight()*0.5);
        textArea.setPrefWidth(500);
        TextArea taMessage = new TextArea();
        taMessage.setWrapText(true);
        taMessage.setPrefHeight(primaryScreenBounds.getHeight()*0.1);
        taMessage.setPrefWidth(500);
        btSend = new Button("Send");
        btSend.setFont(Font.font("Verdana", FontWeight.BOLD, 20));
        btSend.setPrefWidth(300);
        btSend.setDisable(true);
        btSend.setOnAction((EventHandler<ActionEvent>) new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) {
                try {
                    String myMessage = taMessage.getText();
                    if("".equals(myMessage)) myMessage = "Test message;";
                    if(!myMessage.contains(";")) myMessage += ";";
                    os.write(myMessage.getBytes()); //just send a string to the device
                } catch (IOException exception) {
                }

            }
        });
        GridPane gpPlaceHolder = new GridPane();
        gpPlaceHolder.setPrefWidth(300);
        leftGridPane.add(lbBluetooth,0,0,3,1);
        leftGridPane.add(cbBluetooth,0,1,1,1);
        leftGridPane.add(btRefresh,1,1,1,1);
        leftGridPane.add(btDirectory,0,2,1,1);
        leftGridPane.add(tfDirectory,1,2,2,1);

        leftGridPane.add(btConnect,0,4,1,1);
        leftGridPane.add(gpPlaceHolder,1,4,1,1);
        leftGridPane.add(btClear,2,4,1,1);
        leftGridPane.add(textArea,0,5,3,6);
        leftGridPane.add(taMessage,0,12,3,2);
        leftGridPane.add(btSend,2,15,1,1);

        vBoxSide.getChildren().addAll(leftGridPane);
        vBoxSide.isResizable();

        gridPane.add(vBoxSide, 1,1,1,1);
        gridPane.add(vBoxMain, 2,1,4,1);

        primaryStage.setScene(scene);
        primaryStage.show();

        // defining the axes
        final NumberAxis xAxis = new NumberAxis();
        final NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Parameter Values");
        xAxis.setLabel("Time (s)");
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(50);
        yAxis.setAutoRanging(false);
        yAxis.setAnimated(false);
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(MAX_DISPLAY);
        xAxis.setAutoRanging(false);
        xAxis.setAnimated(false);
        xAxis.tickLabelFontProperty().set(Font.font("Verdana", FontWeight.BOLD, 16));
        yAxis.tickLabelFontProperty().set(Font.font("Verdana", FontWeight.BOLD, 16));
        // creating the chart
        lineChart = new LineChart<>(xAxis,yAxis);

        lineChart.setTitle("Real-Time Machining Parameters");
        lineChart.setStyle("-fx-font-size: 24 px; -fx-font-weight: bold;");
        // defining a series
        tempSeries = new XYChart.Series();
        tempSeries.setName("Temperature (C)");
        accSeries = new XYChart.Series();
        accSeries.setName("Acceleration (m/s^2)");
        sg1Series = new XYChart.Series();
        sg1Series.setName("Strain gage 1");
        sg2Series = new XYChart.Series();
        sg2Series.setName("Strain gage 2");
        lineChart.setLegendSide(Side.RIGHT);
        lineChart.setPrefHeight(primaryScreenBounds.getHeight()*0.9);
        vBoxMain.getChildren().addAll(lineChart);
        lineChart.getData().add(tempSeries);
        lineChart.getData().add(accSeries);
        lineChart.getData().add(sg1Series);
        lineChart.getData().add(sg2Series);
        primaryStage.setOnCloseRequest(event -> {
            bKeepReading = false;
            Platform.exit();
            System.exit(0);
        });
        executor.submit(taskScanBluetooth);
    }

    @SuppressWarnings("unchecked")
    private void scanAvailableBluetooth() throws Exception {
        // scan for all devices:
        scanFinished = false;
        LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, new DiscoveryListener() {
            @Override
            @SuppressWarnings({ "CallToPrintStackTrace" })
            public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
                try {
                    String name = btDevice.getFriendlyName(false);
                    System.out.format("%s (%s)\n", name, btDevice.getBluetoothAddress());

                    if (name.matches("DSD TECH HC.*")) {
                    //if (name.matches("Annie.*")) {
                            
                        myBluetooth = btDevice;
                        System.out.println("got it!");
                    }
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
            Thread.sleep(1000);
        }

        // search for services:
        UUID uuid = new UUID(0x1101); //scan for btspp://... services (as HC-05 offers it)
        UUID[] searchUuidSet = new UUID[]{uuid};
        int[] attrIDs = new int[]{ 0x0100 };
        scanFinished = false;
        LocalDevice.getLocalDevice().getDiscoveryAgent().searchServices(attrIDs, searchUuidSet,
        myBluetooth, new DiscoveryListener() {
                    @Override
                    public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
                    }
                    @Override
                    public void inquiryCompleted(int discType) {
                    }
                    @Override
                    public void serviceSearchCompleted(int transID, int respCode) {
                        scanFinished = true;
                    }
                    @Override
                    public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
                        for (ServiceRecord servRecord1 : servRecord) {
                            myBluetoothServiceURL = servRecord1.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
                            try {
                                myBluetoothDevice = servRecord1.getHostDevice().getFriendlyName(false);
                                BTNameURL.put(myBluetoothDevice,myBluetoothServiceURL);
                            } catch (IOException ex) {
                                break;
                            }
                        }
                        scanFinished = true;
                    }
                });
        while (!scanFinished) {
            Thread.sleep(1000);
        }
        Set<String> allBT = BTNameURL.keySet();
        for (String bluetooth : allBT) {
            cbBluetooth.getItems().addAll(bluetooth);
            lbBluetooth.setText("Select your Bluetooth:");
        }
        if(allBT.isEmpty()) {
            lbBluetooth.setText("Search for Bluetooth...");
        } else {
            System.out.println(myBluetoothServiceURL);
        }
        btRefresh.setDisable(false);
    }
    @SuppressWarnings({ "unused" })
    private static void openConnection(String address) throws IOException, InterruptedException {
        try {
            connection = (StreamConnection) Connector.open(address);
            System.out.println("\nServer Started. Waiting for clients to connect...");
            os = connection.openOutputStream();
            os.write("Here is my Java Bluecove test;".getBytes()); //just send a test string to the device
            System.out.println("Connection opened, type in console and press enter to send a message to: " + address);
            LocalDevice localDevice = LocalDevice.getLocalDevice();
        } catch (IOException e) {
        }
        btSend.setDisable(false);
        RemoteDevice device=null;
        if(connection==null) return;
        try {
            device = RemoteDevice.getRemoteDevice(connection);
            input = new BufferedInputStream(connection.openInputStream());
        } catch (IOException e) {
        }
        strWaitingforProcessing="";
        while (bKeepReading) {
            byte buffer[] = new byte[1024];
            int bytesRead;
            try {
                bytesRead = input.read(buffer);
                String incomingMessage = new String(buffer, 0, bytesRead);
                strWaitingforProcessing += incomingMessage;
                Platform.runLater(()->{ process_data(); });
                Thread.sleep(1000);
            } catch (IOException e) {
                System.err.println("Connection closed");
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void process_data()
    {
        if (strWaitingforProcessing.contains(";")) {
            String[] str = strWaitingforProcessing.split(";");
            if (str.length > 1)
            {
                strWaitingforProcessing = str[1];
            }
            else
            {
                strWaitingforProcessing = "";
            }
            String text = str[0];
            String[] strArray = text.split(",");
            int num = strArray.length;
            if (num == 4)
            {
                String[] str1 = strArray[0].split(":");
                String[] str2 = strArray[1].split(":");
                String[] str3 = strArray[2].split(":");
                String[] str4 = strArray[3].split(":");
                double tempValue, accValue, sg1Value, sg2Value;
                if (str1.length == 2 && str2.length == 2 && str3.length == 2 && str4.length == 2) {
                    tempValue = Double.parseDouble(str1[1]);
                    accValue = Double.parseDouble(str2[1]);
                    sg1Value = Double.parseDouble(str3[1]);
                    sg2Value = Double.parseDouble(str4[1]);
                    textArea.appendText(text);
                    textArea.appendText("\r\n");
                    textArea.setScrollTop(Double.MAX_VALUE);
                    System.out.println(text);
                    if(iTotalDataDisplayed>0 && iTotalDataDisplayed % MAX_DISPLAY == 0) {
                        textArea.clear();
                        tempSeries.getData().remove(0,MAX_DISPLAY);
                        accSeries.getData().remove(0,MAX_DISPLAY);
                        sg1Series.getData().remove(0,MAX_DISPLAY);
                        sg2Series.getData().remove(0,MAX_DISPLAY);
                        iTotalDataDisplayed=0;
                        ((ValueAxis<Number>) lineChart.getXAxis()).setLowerBound(iTotalDataDisplayed);
                        ((ValueAxis<Number>) lineChart.getXAxis()).setUpperBound(iTotalDataDisplayed+MAX_DISPLAY);
                    }
                    if(iTotalDataDisplayed==0) {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        String curTime = simpleDateFormat.format(new Date());
                        ((ValueAxis<Number>) lineChart.getXAxis()).setLabel("Time(s) [Start @ " + curTime + "]");
                    }
                    if (iTotalDataToSave==0) {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        startTime = simpleDateFormat.format(new Date());
                        unsavedRecords = "Timestamp: " + startTime + "\r\n";
                        unsavedRecords += "Temperature(C),Acceleration(m/s^2),Strain Gage 1, Strain Gage 2\r\n";
                    }
                    iTotalDataDisplayed++;
                    iTotalDataToSave++;
                    tempSeries.getData().add(new XYChart.Data(iTotalDataDisplayed, tempValue));
                    accSeries.getData().add(new XYChart.Data(iTotalDataDisplayed, accValue));
                    sg1Series.getData().add(new XYChart.Data(iTotalDataDisplayed, sg1Value));
                    sg2Series.getData().add(new XYChart.Data(iTotalDataDisplayed, sg2Value));
                    unsavedRecords += str1[1] + "," + str2[1] + "," + str3[1] + "," + str4[1] + "," + "\r\n";
                    if (iTotalDataToSave>=MAX_SAVE) {
                        save_data();
                        iTotalDataToSave = 0;
                    }
                }
            }
        }
    }

    private static void save_data() {
        FileOutputStream out;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        String date = simpleDateFormat.format(new Date());
        try {
            byte b[] = unsavedRecords.getBytes();
            String outputFileName = strDirectory + "\\Data-" + date + ".csv";
            out = new FileOutputStream(outputFileName);
            out.write(b);
            out.close();
        } catch (java.io.IOException e) {
                System.out.println(e.toString());
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        launch(args);
    }
}


