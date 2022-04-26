package it.polimi.ingsw.model;

import java.util.ArrayList;

/**
 *This Class represents one single cloud-tile
 */
public class CloudTile {
    /**
     * This attribute is the identifier of the cloud, which is unique
     */
    private int ID;
    /**
     * This attribute is the number of students that can be on the cloud at the same time
     */
    private int capacity;
    /**
     * This attribute is the list of students currently on the cloud
     */
    private ArrayList<Creature> students;
    /**
     * This attribute is the reference to the bag used in the match, it can be called to draw the
     * needed students
     */
    private Bag bag;

    /**
     * Constructor
     * @param ID identifier of the cloud
     * @param numOfPlayers number of players playing the match
     * @param bag reference to the bag used in the match
     */
    public CloudTile(int ID, int numOfPlayers, Bag bag){
        this.ID = ID;

        if(numOfPlayers == 3){
            this.capacity = 4;
        }else{
            this.capacity = 3;
        }
        this.bag = bag;
        this.students = bag.drawStudents(capacity);
    }

    /**
     * Copies the "students" attribute into a new ArrayList and replace old students with new ones, drawn
     * from the "bag"
     * @return ArrayList of all students found on the cloud
     */
    public ArrayList<Creature> takeStudents(){

        ArrayList<Creature> takenStudents = new ArrayList<Creature>(this.students);
        this.students.clear();

        //put new students on the cloud
        putStudents(bag.drawStudents(capacity));

        return takenStudents;
    }

    /**
     * Puts new students on the cloud; it's private because it's only called by the methode "takeStudents"
     * @param newStudents ArrayList of new students to put on the cloud
     */
    private void putStudents(ArrayList<Creature> newStudents) {
        this.students = newStudents;
    }
}
