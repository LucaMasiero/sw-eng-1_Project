package it.polimi.ingsw.controller.characterCards;

import com.google.gson.Gson;
import it.polimi.ingsw.controller.Controller;
import it.polimi.ingsw.controller.SupportFunctions;
import it.polimi.ingsw.model.Creature;
import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.schoolboard.DiningRoom;
import it.polimi.ingsw.model.schoolboard.Entrance;
import it.polimi.ingsw.network.messages.Message;
import it.polimi.ingsw.network.messages.clientMessages.CharacterDataMessage;
import it.polimi.ingsw.network.messages.serverMessages.AckCharactersMessage;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class represents the character called "bard" (the third to last on the rules file).
 * It allows the players to choose 1 or 2 students from the player's entrance and switch them with,
 * respectively, 1 or 2 students from the player's dining room;
 * if the players gets more students in the dining room than other players he takes control over the
 * corresponding professor.
 */
public class Bard extends Character {
    public Bard(Controller controller) {
        this.controller = controller;
        this.price = 1;
    }

    /**
     * This method controls if there is at least one student in the dining room to use for the
     * switch.
     * @return true if there is at least one student in the dining room, false otherwise
     */
    @Override
    public boolean checkCharacterAvailability() {
        String json = controller.getMsg();
        Gson gson = new Gson();
        Message request = gson.fromJson(json, Message.class);
        int player_ID = request.getSender_ID();

        Player user = controller.getMatch().getPlayerByID(player_ID);
        Entrance entrance = user.getSchoolBoard().getEntrance();
        DiningRoom diningRoom = entrance.getDoorToTheDiningRoom();

        // check if there is at least one student in the dining room
        return diningRoom.getTotalNumberOfStudents() > 0;
    }

    @Override
    public void effect(CharacterDataMessage request) {
        increasePrice();

        Player player = controller.getMatch().getPlayerByID(request.getSender_ID());
        Entrance entrance = player.getSchoolBoard().getEntrance();
        DiningRoom diningRoom = player.getSchoolBoard().getDiningRoom();

        // get the students chosen by the player
        ArrayList<Integer> studentsFromEntrance = request.getStudentsFromPlayerEntrance();
        ArrayList<Creature> studentsFromDiningRoom = request.getStudentsFromPlayerDiningRoom();

        // find who are the professors' masters before using the card
        HashMap<Creature, Integer> previousProfessorsMaster = new HashMap<Creature, Integer>();
        for(Creature c: Creature.values()){
            previousProfessorsMaster.put(c, SupportFunctions.whoControlsTheProfessor(controller.getMatch(), c));
        }

        int entrance_ID;
        // SWITCH STUDENTS
        for(int i = 0; i < studentsFromEntrance.size(); i++){
             entrance_ID = studentsFromEntrance.get(i);

            diningRoom.removeStudents(1, studentsFromDiningRoom.get(i));
            diningRoom.addStudent(entrance.getStudentsInTheEntrance().get(entrance_ID));

            entrance.removeStudent(entrance_ID);
            entrance.addStudent(studentsFromDiningRoom.get(i));
        }

        // find who are the professors' masters after the card has been used
        HashMap<Creature, Integer> currentProfessorsMaster = new HashMap<Creature, Integer>();
        for(Creature c: Creature.values()){
            currentProfessorsMaster.put(c, SupportFunctions.whoControlsTheProfessor(controller.getMatch(), c));
        }

        // CHECK PROFESSORS' CONTROL
        SupportFunctions.checkProfessorsControl(controller, previousProfessorsMaster, currentProfessorsMaster);

        // create and send the ack message
        int coinsReserve = controller.getMatch().getCoinsReserve();
        AckCharactersMessage ack = new AckCharactersMessage(request.getSender_ID(), "bard", coinsReserve);

        ack.setEntranceOfPlayer(player.getSchoolBoard().getEntrance().getStudentsInTheEntrance());
        ack.setPlayerDiningRoom(player.getSchoolBoard().getDiningRoom().getOccupiedSeats());

        for(int k = 0; k < controller.getNumberOfPlayers(); k++){
            player = controller.getMatch().getPlayerByID(k);
            ack.setPlayerProfessors(k, player.getMyProfessors());
        }

        int coinsOfPlayer = controller.getMatch().getPlayerByID(request.getSender_ID()).getCoinsOwned();
        ack.setPlayerCoins(coinsOfPlayer);
        controller.sendMessageAsBroadcast(ack);
    }
}
