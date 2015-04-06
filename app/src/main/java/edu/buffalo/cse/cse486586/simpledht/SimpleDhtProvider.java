package edu.buffalo.cse.cse486586.simpledht;
import java.util.Hashtable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;

import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {
    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<Runnable>(128);
    static final Executor myPool = new ThreadPoolExecutor(60,120,1, TimeUnit.SECONDS,sPoolWorkQueue);
    //Other Variables
    static final String TAG = SimpleDhtProvider.class.getSimpleName();
    static String myPort = "";
    static String hashedPort = "";
    static String originator = "";
    static ChordLinkedList chord;
    static final String masterJoiner = "11108";
    static final String separator = "---";
    static final String valSeparator = "##";
    static String next = "";
    static String nextPort = null;
    static String pre = "";
    static String prePort = null;
    static boolean isRequester = true;

    //Maps and Set
    static Hashtable<String,String> remotePorts = new Hashtable<>();
    static HashSet<String> masterConSet = new HashSet<>();
    static HashSet<String> lookingSet = new HashSet<>();
    static HashMap<String, String> myMessageMap = new HashMap<>();
    private static BlockingQueue<String> queue = new SynchronousQueue<String>();
    private static BlockingQueue<String> queueAll = new SynchronousQueue<String>();
    private static Uri mUri = null;
    //The Database reference needed for storage
    private SQLiteDatabase messengerDb;
    //A reference for Db Helper Class
    private static GroupMessageDbHelper dbHelper;

    //Db Attributes
    private static final String DB_NAME = "messagesDb";
    private static final int DB_VERSION = 3;

    //Db Table and Column Name
    private String TABLE_NAME = "tblchatMessage";
    private static final String KEY_COL = "key";
    private static final String VAL_COL = "value";


    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    public static void startUp(String myPortNum)
    {
        myPort = myPortNum;
        try {
            remotePorts.put("11108","5554");
            remotePorts.put("11120","5560");
            remotePorts.put("11116","5558");
            remotePorts.put("11124","5562");
            remotePorts.put("11112","5556");
            hashedPort = genHash(remotePorts.get(myPort));
//            hashedMaster = genHash(remotePorts.get(masterJoiner));
            prePort = null;
            nextPort = null;

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
//        if(!myPort.equals(masterJoiner))
//        {
//            chord.addNode(hashedMaster,masterJoiner);
//        }
//        ChordLinkedList.myPort = myPort;
    }
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        messengerDb = dbHelper.getWritableDatabase();
        Log.v("Delete at "+myPort, "Key= "+selection);
        String select = "\"*\"";
        Log.v(selection.equals(select)+""," Select");
        int num = 0;

        if(selection.equals("\"@\"")) {
            num = messengerDb.delete(TABLE_NAME, null, null);
        }
        else if(selection.equals("\"*\""))
        {
            num = messengerDb.delete(TABLE_NAME, null, null);
            String[] msg = new String[3];
            msg[0] = "delete";
            msg[1] = myPort;
            msg[2] = nextPort;
            new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msg);
        }
        else
        {
            num = messengerDb.delete(TABLE_NAME,"key = \'"+selection+"\'",null);
        }
        return num;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        try {
            String hashKey = genHash(values.get(KEY_COL).toString());

            String whereToStore = lookUpNode(values.get(KEY_COL).toString(), hashKey, values.getAsString(VAL_COL));

            if (whereToStore.equals(myPort)) {
                ContentValues cv = new ContentValues();
//                cv.put(KEY_COL,hashKey);
//                cv.put(VAL_COL,values.get(VAL_COL).toString());
                myInsert(values);
            }
        }
        catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return uri;
    }

    private void myInsert(ContentValues values) {
        messengerDb = dbHelper.getWritableDatabase();
        Cursor cur = messengerDb.query(TABLE_NAME, new String[]{VAL_COL}, KEY_COL + "='" + values.get(KEY_COL) + "'", null, null, null, null);

        if (cur.getCount() > 0) {
            messengerDb.update(TABLE_NAME, values, "value = '" + values.get(VAL_COL) + "'", null);
        }
        else
        {
            long rowId = messengerDb.insert(TABLE_NAME, null, values);
            if (rowId > 0) {
//                Uri CONTENT_URI = Uri.parse(uri.toString() + "/" + TABLE_NAME);
//                Uri myUri = ContentUris.withAppendedId(CONTENT_URI, rowId);
//                getContext().getContentResolver().notifyChange(myUri, null);
                myMessageMap.put(values.get(KEY_COL).toString(),values.get(VAL_COL).toString());
                Log.v(TAG,"insert at "+myPort +" "+ values.toString());
//                return myUri;
            }
        }
    }

    //Looking for the right position for the key
    private synchronized static String lookUpNode(String key, String hashKey, String value)
    {
        Log.v(TAG,"Inside Lookup for "+key);
        if(nextPort == null && prePort == null)
            return myPort;

        else if(nextPort.equals(myPort))
            return myPort;

        else
        {
            if(pre.compareTo(hashedPort) > 0 && (hashKey.compareTo(pre) > 0 || hashKey.compareTo(hashedPort) < 0)) {
                Log.v(TAG,"I will keep");
                return myPort;
            }
            else if(hashedPort.compareTo(hashKey) >= 0 && hashKey.compareTo(pre) > 0)
            {
                Log.v(TAG,"I will keep");
                return myPort;
            }
            else {
                Log.v(TAG,"Sending message to "+nextPort);
                String msgToSend = "looking"+ separator + key + separator + value + separator + myPort + separator + hashKey;
                String[] msg = new String[3];
                msg[0] = "looking";
                msg[1] = nextPort;
                msg[2] = msgToSend;
//                lookingSet.add(key);
                new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msg);
                return nextPort;
            }
        }
    }

    @Override
    public boolean onCreate() {

        dbHelper = new GroupMessageDbHelper(getContext(), DB_NAME, DB_VERSION, TABLE_NAME);

        chord = new ChordLinkedList();
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {

        if(dbHelper == null)
        {
            Log.v("dbHelper is null","");
        }
//            dbHelper = new GroupMessageDbHelper(getContext(),DB_NAME, DB_VERSION, TABLE_NAME);
        messengerDb = dbHelper.getReadableDatabase();

        SQLiteQueryBuilder qBuilder = new SQLiteQueryBuilder();
        qBuilder.setTables(TABLE_NAME);
        Cursor resultCursor = null;
        Log.v("Query at "+myPort, "Key= "+selection);
        String select = "\"*\"";
//        Log.v(selection.equals(select)+""," Select");
        if(selection.equals(select))
        {
//            Log.v("1","*");
            resultCursor = qBuilder.query(messengerDb, projection, null,
                    selectionArgs, null, null, sortOrder);
            Log.v(TAG,"Count: "+resultCursor.getCount());
            if(nextPort == null && prePort == null)
                return resultCursor;
            else if(nextPort != null && nextPort.equals(myPort))
                return resultCursor;
            Log.v(TAG, "Is he Requester: "+isRequester+"");
            if(isRequester)
            {
                try {
                    originator = myPort;
                    MatrixCursor mx = getResults(selection,originator);
                    mx.moveToLast();
                    Log.v(TAG,"Before Merging Count "+mx.getCount());
                    if(resultCursor.getCount() > 0) {
                        resultCursor.moveToFirst();
                        String mm = getCursorValue(resultCursor);
                        String[] received = mm.split(valSeparator);
                        for (String m : received) {
                            Log.v(TAG, "Merging my results");
                            String[] keyVal = m.split(" ");
                            mx.addRow(new String[]{keyVal[0], keyVal[1]});
                        }
                    }
                    mx.moveToFirst();
                    Log.v(TAG,"After Merging Count "+mx.getCount());
                    return mx;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            else
            {
                if(originator.equals(nextPort))
                    return resultCursor;
                else
                    try {
                        MatrixCursor mx = getResults(selection,originator);
                        mx.moveToLast();
                        Log.v(TAG,"Before Merging Count "+mx.getCount());
                        if(resultCursor.getCount() > 0) {
                            resultCursor.moveToFirst();
                            String mm = getCursorValue(resultCursor);
                            String[] received = mm.split(valSeparator);
                            for (String m : received) {
                                Log.v(TAG,"Merging my results");
                                String[] keyVal = m.split(" ");
                                mx.addRow(new String[]{keyVal[0], keyVal[1]});
                            }
                        }
                        Log.v(TAG,"After Merging Count "+mx.getCount());
                        mx.moveToFirst();
                        return mx;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
            }
        }
//s
        else if(selection.equals("\"@\""))
        {
            Log.v(TAG,"Query "+selection);
            resultCursor = qBuilder.query(messengerDb, projection, null,
                    selectionArgs, null, null, sortOrder);
            Log.v(TAG,"Count "+ resultCursor.getCount());
        }
        else {
            Log.v(TAG,"Query a single key "+selection);
            resultCursor = qBuilder.query(messengerDb, projection, " key = \'" + selection + "\' ",
                    selectionArgs, null, null, sortOrder);

            if(resultCursor != null && resultCursor.getCount() <= 0)
            {
                try {
                    return getResults(selection,myPort);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    }
            }
        }

//        Log.v("query", resultCursor.getString(1));
        return resultCursor;
    }

    private MatrixCursor getResults(String selection, String origin) throws InterruptedException {
        Log.v(TAG,"Inside GetResults Class");
        Log.v("Sending Query to ",nextPort);
        String[] msg = new String[4];
        msg[0] = "query";
        msg[1] = selection;
        msg[2] = nextPort;
        msg[3] = origin;
        new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msg);
        Log.v(TAG,"Waiting...");
        String message = queue.take();
        isRequester = true;
        Log.v(TAG,"Gotcha Message "+message+" at "+myPort);
        //Blocked here
        MatrixCursor mx = new MatrixCursor(new String[]{KEY_COL,VAL_COL},50);
        if(!message.equals("")) {
            String[] received = message.split(valSeparator);
            for (String m : received) {
                Log.v(TAG, "Merging my results");
                String[] keyVal = m.split(" ");
                mx.addRow(new String[]{keyVal[0], keyVal[1]});
            }
        }
        Log.v(TAG,"GetResults Count "+mx.getCount());
        return mx;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private static String genHash(String input) throws NoSuchAlgorithmException {

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    public class GroupMessageDbHelper extends SQLiteOpenHelper
    {
        private String CREATE_TABLE = ""; //= "tbl_chatMessage";

        public GroupMessageDbHelper(Context context, String dbName, int dbVersion, String tableName)
        {
            super(context, dbName, null, dbVersion);
            if(!"".equals(tableName))
            {
                CREATE_TABLE = "CREATE TABLE " +
                        tableName +  " ( " + KEY_COL + " TEXT PRIMARY KEY, " + VAL_COL + " TEXT )";
            }
            //messengerDb.execSQL(CREATE_TABLE);

        }

        @Override
        public void onCreate(SQLiteDatabase db)
        {
            db.execSQL(CREATE_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    public static class ClientTask extends AsyncTask<String, String, Void> {

        @Override
        protected Void doInBackground(String... params) {

            String msgToSend = "";
            String portToSend = "";

            if(params[0].equals("JoinMaster")) {
                msgToSend = params[0] + separator + params[1];
                portToSend = params[2];
                sendMessage(portToSend,msgToSend);
            }

            else if(params[0].equals("Next") || params[0].equals("Pre")) {
                msgToSend = params[0] + separator + params[1] + separator + params[2];
                portToSend = params[3];
                sendMessage(portToSend,msgToSend);
            }

            else if(params[0].equals("PreNext")) {
                msgToSend = params[0] + separator + params[1]+ separator + params[2]+ separator
                        + params[3]+ separator + params[4];
                sendMessage(params[5],msgToSend);
            }
            else if(params[0].equals("looking"))
            {
                msgToSend = params[2];
                portToSend = params[1];
                sendMessage(portToSend,msgToSend);
            }
            else if(params[0].equals("updateNext"))
            {
                msgToSend = params[0] + separator + params[1] + separator +  params[2];
                portToSend = params[3];
                sendMessage(portToSend,msgToSend);
            }
            else if(params[0].equals("RequestAck"))
            {
                msgToSend = params[0] + separator + params[1] + separator +  params[2];
                portToSend = params[3];
                sendMessage(portToSend,msgToSend);
            }
            else if(params[0].equals("query"))
            {
                msgToSend = params[0] + separator + params[1] + separator + myPort +separator + params[3];
                portToSend = params[2];
                sendMessage(portToSend,msgToSend);
            }
            else if(params[0].equals("queryReply"))
            {
                msgToSend = params[0] + separator + params[1] + separator + params[2];
                portToSend = params[3];
                sendMessage(portToSend,msgToSend);
            }
            else if(params[0].equals("giveAll"))
            {
                msgToSend = params[0] + separator + params[1] + separator + params[2];
                portToSend = params[3];
                sendMessage(portToSend,msgToSend);
            }
            else if(params[0].equals("delete"))
            {
                msgToSend = params[0] + separator + params[1];
                portToSend = params[2];
                sendMessage(portToSend,msgToSend);
            }
            return null;
        }

        private void sendMessage(String portToSend, String msgToSend)
        {
            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(portToSend));
                OutputStream out = socket.getOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(out);
                writer.write(msgToSend);
                writer.flush();
                writer.close();
                out.close();
                socket.close();
            }
            catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            }
            catch (IOException e) {
                Log.e(TAG, "ClientTask IOException");
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
        }
    }

    public class ServerTask extends AsyncTask<ServerSocket, String, Void>{

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            try {
                while (true)
                {
                    Log.v(TAG,"Inside Server "+myPort);
                    Socket socket = serverSocket.accept();
//                    Log.v(TAG,"Socket Accepted");
                    InputStream read = socket.getInputStream();
//                    Log.v(TAG,"InputStream Created");
                    String msgReceived = "";
                    InputStreamReader reader = new InputStreamReader(read);
//                    Log.v(TAG,"InputStreamReader Created");
                    BufferedReader buffer = new BufferedReader(reader);
//                    Log.v(TAG,"BufferedReader Created");
                    msgReceived = buffer.readLine();
                    Log.v(TAG,"Message Received :"+msgReceived);
                    String[] msgs = msgReceived.split(separator);

                    if(msgs[0].equals("JoinMaster"))
                    {
                        Log.d(TAG,"JoinMaster "+myPort);
                        String hash = genHash(remotePorts.get(msgs[1]));

                        if(nextPort == null && prePort == null)
                        {
                            nextPort = msgs[1];
                            prePort = msgs[1];
                            next = hash;
                            pre = hash;

                            String[] msgToSuc = new String[6];
                            msgToSuc[0] =  "PreNext";
                            msgToSuc[1] = myPort;//Pre
                            msgToSuc[2] = hashedPort;
                            msgToSuc[3] = myPort;//next
                            msgToSuc[4] = hashedPort;
                            msgToSuc[5] = msgs[1];
                            new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msgToSuc);
                        }
                        else {
                            boolean cond1 = (hash.compareTo(hashedPort) > 0 && hash.compareTo(next) < 0);
                            boolean cond2 = (hashedPort.compareTo(next) > 0 && (hash.compareTo(hashedPort) > 0 || next.compareTo(hash) > 0));
                            if (cond1 || cond2) {
                                Log.v("In between ", msgs[1]);
                                String[] msgToSuc = new String[6];
                                msgToSuc[0] = "PreNext";
                                msgToSuc[1] = myPort;//Pre
                                msgToSuc[2] = hashedPort;
                                msgToSuc[3] = nextPort;//next
                                msgToSuc[4] = next;
                                msgToSuc[5] = msgs[1];
                                new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msgToSuc);

                                String[] msg = new String[4];
                                msg[0] = "Pre";
                                msg[1] = msgs[1];
                                msg[2] = hash;
                                msg[3] = nextPort;
                                new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msg);

                                next = hash;
                                nextPort = msgs[1];
                                Log.v(myPort + " ", "Pre " + prePort + " Next " + nextPort);
                            } else if ((hash.compareTo(hashedPort) < 0 && hash.compareTo(pre) > 0) ||
                                    (pre.compareTo(hashedPort) > 0 && (hash.compareTo(pre) > 0 || hash.compareTo(hashedPort) < 0))) {
                                Log.v("In between ", msgs[1]);
                                String[] msgToSuc = new String[6];
                                msgToSuc[0] = "PreNext";
                                msgToSuc[1] = prePort;//Pre
                                msgToSuc[2] = pre;
                                msgToSuc[3] = myPort;//next
                                msgToSuc[4] = hashedPort;
                                msgToSuc[5] = msgs[1];
                                new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msgToSuc);

                                String[] msg = new String[4];
                                msg[0] = "Next";
                                msg[1] = msgs[1];
                                msg[2] = hash;
                                msg[3] = prePort;
                                new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msg);

                                pre = hash;
                                prePort = msgs[1];
                                Log.v(myPort + " ", "Pre " + prePort + " Next " + nextPort);
                            } else {
                                Log.v("Sending to next ", msgs[1]);
                                if (!nextPort.equals(masterJoiner)) {
                                    String[] msg = new String[3];
                                    msg[0] = "JoinMaster";
                                    msg[1] = msgs[1];
                                    msg[2] = nextPort;
                                    new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msg);
                                }
                            }
                        }
                    }
                    else if(msgs[0].equals("PreNext"))
                    {
                        prePort = msgs[1];
                        pre = msgs[2];
                        nextPort = msgs[3];
                        next = msgs[4];
                        Log.v(myPort+" ","Pre "+prePort+" Next "+nextPort);
                    }
                    else if(msgs[0].equals("Next"))
                    {
                        Log.d("By "+msgs[1],"Next "+myPort);
                        nextPort = msgs[1];
                        next = msgs[2];
                        Log.v(myPort + " ", "Pre " + prePort + " Next " + nextPort);
                    }

                    else if(msgs[0].equals("Pre"))
                    {
                        Log.d("By "+msgs[1],"Pre "+myPort);
                        prePort = msgs[1];
                        pre = msgs[2];
                        Log.v(myPort + " ", prePort + " " + nextPort);
                    }

                    else if(msgs[0].equals("RequestAck"))
                    {
                        Log.d(myPort," "+msgs[0]+" "+msgs[1]);
                        nextPort = msgs[1];
                        next = genHash(nextPort);
                        prePort = msgs[2];
                        pre = genHash(prePort);
                    }

                    else if(msgs[0].equals("looking"))
                    {
                        Log.d(TAG,"looking Message for "+msgs[1]);
                        String portNum = lookUpNode(msgs[1],msgs[4],msgs[2]);
                        if(portNum.equals(myPort))
                        {
                            Log.v("I will save ",msgs[1]);
                            ContentValues cv = new ContentValues();
                            cv.put(KEY_COL, msgs[1]);
                            cv.put(VAL_COL, msgs[2]);
                            myInsert(cv);
                        }
                    }

                    else if(msgs[0].equals("query"))
                    {
                        Log.d(TAG,myPort+" "+msgs[0]+" "+msgs[1]);
                        if(mUri == null)
                            mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
                        originator = msgs[3];
                        isRequester = false;
                        publishProgress(new String[]{msgs[0],msgs[1],msgs[2]});
                    }

                    else if(msgs[0].equals("queryReply"))
                    {
                        if(msgs.length > 2) {
                            Log.d(TAG, "Got a reply Mate " + msgs[2]);
                            queue.put(msgs[2]);
                        }
                        else
                        {
                            queue.put("");
                        }
//                        publishProgress(new String[]{msgs[0],msgs[1],msgs[2]});
                    }

                    else if(msgs[0].equals("giveAll"))
                    {
                        Log.d(myPort," "+msgs[0]+" Originator: "+msgs[2] +" and myport: "+myPort);
                        if(msgs[2].equals(myPort)) {
                            Log.v(TAG,"I received back my Give All");
                            queueAll.put(msgs[1]);
                        }

                        else {
                            Log.v(TAG,"Give all-I am not the originator");
                            if (mUri == null)
                                mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
                            isRequester = false;
                            Cursor resultCursor = query(mUri, null,
                                    "\"*\"", null, null);
                            isRequester = true;
                            String myMessage = getCursorValue(resultCursor);
                            String[] msg = new String[4];
                            msg[0] = "giveAll";
                            msg[1] = myMessage + msgs[1];
                            msg[2] = msgs[2];
                            msg[3] = nextPort;
                            new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msg);
                        }
                    }

                    else if(msgs[0].equals("delete"))
                    {
                        Log.d(TAG,msgs[0]+" "+msgs[1]);

                        if(!msgs[1].equals(myPort))
                        {
                            delete(mUri,"\"*\"",null);
                            if(!nextPort.equals(msgs[1])) {
                                String[] msg = new String[3];
                                msg[0] = "delete";
                                msg[1] = msgs[1];
                                msg[2] = nextPort;
                                new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msg);
                            }
                        }
//                        queue.put(msgs[2]);
                    }
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                Log.v(TAG, "Error in ServerTask::"+ex.getMessage());
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);

            if(values[0].equals("query"))
            {
                Log.d(TAG,"Publish Query");
                Cursor resultCursor = query(mUri, null,
                        values[1], null, null);
                Log.d(TAG,"Got the Results "+resultCursor.getCount()+"");
                String message = "";
                if(resultCursor.getCount() > 0)
                    message = getCursorValue(resultCursor);
                isRequester = true;
                Log.v(TAG,"Now replying back with "+message);
                String[] msgToRequester = new String[4];
                msgToRequester[0] = "queryReply";
                msgToRequester[1] = values[1];
                msgToRequester[2] = message;
                msgToRequester[3] = values[2];
                new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msgToRequester);
            }
            else {
                if (values[0].equals("queryReply")) {
                    try {
                        queue.put(values[2]);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }


//                else if(pre.compareTo(hashedPort) > 0 && (hash.compareTo(hashedPort) > 0 && hash.compareTo(pre) > 0 || hash.compareTo(hashedPort) < 0 && hash.compareTo(pre) < 0))
//                {
//                    String[] msgToSuc = new String[6];
//                    msgToSuc[0] = "PreNext";
//                    msgToSuc[1] = nextPort;//Pre
//                    msgToSuc[2] = next;
//                    msgToSuc[3] = myPort;//next
//                    msgToSuc[4] = hashedPort;
//                    msgToSuc[5] = msgs[1];
//                    new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msgToSuc);
//
//                    String[] msg = new String[4];
//                    msg[0] = "Next";
//                    msg[1] = msgs[1];
//                    msg[2] = hash;
//                    msg[3] = prePort;
//                    new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msg);
//
//                    pre = hash;
//                    prePort = msgs[1];
//                    Log.v(myPort + " ", "Pre " + prePort + " Next " + nextPort);
//
//                }
//                else
//                {
//                    String[] msg = new String[4];
//                    msg[0] =  "updateNext";
//                    msg[1] = msgs[1];
//                    msg[2] = hash;
//                    msg[3] = nextPort;
//                    new ClientTask().executeOnExecutor(SimpleDhtProvider.myPool, msg);
//                }//
                }
            }
        }
    }

    private String getCursorValue(Cursor resultCursor) {
        Log.v(TAG,"Converting to String");
//        StringBuilder sb = new StringBuilder();
        resultCursor.moveToFirst();
        int valueIndex = resultCursor.getColumnIndex(VAL_COL);
        int keyIndex = resultCursor.getColumnIndex(KEY_COL);
        String result = "";
        boolean isLast = true;
        while(resultCursor.getCount() > 0 && isLast)
        {
            String newKey = resultCursor.getString(keyIndex);
            String newValue = resultCursor.getString(valueIndex);
//            Log.v(TAG,"Appending "+newKey+" "+newValue);
//            sb.append(newKey+" "+newValue+separator);
            result = result+newKey+" "+newValue+valSeparator;
            isLast = resultCursor.moveToNext();
        }
        Log.v(TAG,"Final Building: "+result);
        return result;
    }
}
