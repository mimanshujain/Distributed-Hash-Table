package edu.buffalo.cse.cse486586.simpledht;

import java.util.Iterator;
import java.util.Stack;

/**
 * Created by sherlock on 3/25/15.
 */

class Node{

    Node next;
    Node previous;
    String key;
    String portNum;

    public Node(String key, String portNum)
    {
        next = null;
        previous = null;
        this.key = key;
        this.portNum = portNum;
    }
}

public class ChordLinkedList {

    static String next = "";
    static String nextPort = "";
    static String pre = "";
    static String prePort = "";

    Node head;
    static String myPort = "";
    public ChordLinkedList()
    {
        head = null;
    }

    public boolean isEmpty()
    {
        return head == null;
    }

    public void addNode(String key, String portNum)
    {
//        String result = "";
        if(isEmpty())
        {
            head = new Node(key, portNum);
            head.next = head;
            head.previous = head;
//            nextPort = head.portNum;
//            prePort = head.portNum;
        }
        else {
            Node temp = new Node(key, portNum);
            Node n = head;
            Node nn;//= n.previous;

            if(n.key.equals(n.next.key))
            {
                temp.next = n;
                temp.previous = n;
                n.next = temp;
                n.previous = temp;
            }
            else
            {
                while (true)
                {
                    if(n.previous.key.equals(head.key))
                    {
                        if(key.compareTo(n.key) < 0 && key.compareTo(head.key) < 0) {
                            n.previous = temp;
                            temp.next = n;
                            temp.previous = head;
                            head.next = temp;
                            break;
                        }
                        else
                        {
                            n.next.previous = temp;
                            temp.next = n.next.previous;
                            temp.previous = n;
                            n.next = temp;
                            break;
                        }
                    }
                    else if(key.compareTo(n.key) < 0)
                        n = n.previous;
                    else if(key.compareTo(n.key) > 0)
                    {
                        nn = n.next;
                        nn.previous = temp;
                        temp.next = nn;
                        temp.previous = n;
                        n.next = temp;
                        break;
                    }
                }
            }

            next = temp.next.key;
            nextPort = temp.next.portNum;
            pre = temp.previous.key;
            prePort = temp.previous.portNum;

//            if(key.compareTo(head.key) >= 0)
//                head = temp;
//            while (true)
//            {
//                if(n.key.compareTo(key) < 0)
//                {
//                    previous = n;
//                    n = n.next;
//                    if(n == null)
//                    {
//                        previous.next = temp;
//                        temp.previous = previous;
//                        temp.next = head;
//                        head.previous = temp;
//                        break;
//                    }
//                }
//                else
//                {
//                    previous.next = temp;
//                    temp.previous = previous;
//                    temp.next = n;
//                    n.previous = temp;
//                    break;
//                }
//            }
        }
//        resetHead();
//        return result;
    }

    public String lookUp(String key)
    {
        //Checking corner case
        if(head.previous.key.compareTo(head.key) > 0 && checkLexicoAllNodes(key,1))
            return myPort;
        else if(head.previous.key.compareTo(head.key) < 0 && checkLexicoAllNodes(key,2))
            return myPort;
        else {
            if(key.compareTo(head.key) <= 0 && key.compareTo(head.previous.key) > 0)
                return myPort;
            return head.next.portNum;
        }
    }

    private boolean checkLexicoAllNodes(String key, int order)
    {
        Node n = head;
        if(order == 1) {
            while (true) {
                if (n.key.compareTo(key) > 0)
                    return false;
                n = n.next;
                if(n.portNum.equals(myPort))
                    break;
            }
            return true;
        }
        else
        {
            while (true) {
                if (n.key.compareTo(key) < 0)
                    return false;
                n = n.next;
                if(n.portNum.equals(myPort))
                    break;;
            }
            return true;
        }
    }

    private void resetHead()
    {
        Node n = head;

        while(n != null && n.next != n)
        {
            if(n.portNum.equals(myPort))
            {
                head = n;
                break;
            }
            n = n.next;
        }
    }
}
