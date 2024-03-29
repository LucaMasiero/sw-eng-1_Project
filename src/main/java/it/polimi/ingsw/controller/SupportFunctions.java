package it.polimi.ingsw.controller;

import it.polimi.ingsw.controller.characterCards.MushroomsMerchant;
import it.polimi.ingsw.model.*;

import java.util.ArrayList;
import java.util.HashMap;

public class SupportFunctions {

    /**
     * This method finds out who is the first player of the upcoming new round
     * @param controller reference to the controller
     * @return ID of the first player of next round, who is also the nextPlayer
     */
    static int findFirstPlayerOfNewRound(Controller controller){

        int tempFirstPlayer = -1;
        int index;

        for(index = 0; index < controller.getNumberOfPlayers(); index++){
            if(!controller.getPlayersDisconnected().get(index)){
                tempFirstPlayer = controller.getActionPhaseOrder().get(index);
                break;
            }
        }

        return tempFirstPlayer;
    }

    /**
     *  This method controls if there are only three islands left, if so the match will end
     * @param match reference to the match in question
     * @return true if there are 3 islands left
     *         false if there are more than three islands
     */
    static public boolean onlyThreeIslandsLeft(Match match){
        int islandsLeftCounter = 0;

        ArrayList<Archipelago> islands = match.getRealmOfTheMatch().getArchipelagos();
        for(Archipelago a: islands){
            if(a != null){
                islandsLeftCounter++;
            }
        }

        assert islandsLeftCounter > 3 || islandsLeftCounter == 3 : "ERROR: there are less than 3 islands left";
        return islandsLeftCounter == 3;
    }

    /**
     * This method controls if there are no more students in the bag to be drawn,
     * if so the match will end
     * @param match reference to the match in question
     * @return true if there are no more students to draw
     *         false if there are some more students in the bag
     */
    static boolean noMoreStudentsToDraw(Match match){
        Bag bag = match.getBagOfTheMatch();

        assert bag.getNumberOfRemainingStudents() >= 0 : "ERROR: the number of students in the bag is less than zero.";
        return bag.getNumberOfRemainingStudents() == 0;
    }

    /**
     * This method controls if there are no more students inside the bag (the last one was drawn during the last round)
     * @param controller reference to the controller of the match
     * @return true if the match must end, false otherwise
     */
    static boolean emptyBag_control(Controller controller){
        boolean matchMustEnd = false;

        // control if a player has no more assistant cards
        ArrayList<Player> players = controller.getMatch().getPlayers();

        for(Player p: players){
            if (p.getAssistantsDeck().getNumberOfRemainingCards() == 0) {
                matchMustEnd = true;
                break;
            }
        }

        return matchMustEnd;
    }

    /**
     * This method controls if one between all the players has no more assistant cards to use
     * @param controller reference to the controller of the match
     * @return true if the match must end, false otherwise
     */
    static boolean playerWithNoMoreAssistants_control(Controller controller){
        boolean matchMustEnd = false;


        Bag bag = controller.getMatch().getBagOfTheMatch();

        if(bag.getNumberOfRemainingStudents() == 0){
            matchMustEnd = true;
        }

        return matchMustEnd;
    }

    /**
     * This method will end the match by sending an end-of-match message to all the players and by
     * notifying each ClientHandler that the connection to the clients can be turned off
     * @param controller reference to the controller of the match
     * @param reason reason why the match ended, put in the message
     * @param winner ID of the player who's the winner of this match
     */
    static public void endMatch(Controller controller, String reason, int winner){
        String winnerNickname = controller.getPlayersNickname().get(winner);

        EndOfMatchMessage finalMessage = new EndOfMatchMessage(winner, winnerNickname, reason);
        controller.sendMessageAsBroadcast(finalMessage);

        // set to true the attribute matchEnded inside the Controller
        controller.setMatchEnded(true);
    }

    /**
     * This method will end the match, computing the winner and sending an end-of-match message
     * to all the players; finally it notifies each ClientHandler that the connection to the clients
     * can be turned off
     * @param controller reference to the controller of the match that must end
     * @param reason reason why the match ended
     */
    static public void endMatch(Controller controller, String reason){
        int winner = computeWinner(controller);
        String winnerNickname = null;

        if(!(winner == -1)){
            winnerNickname = controller.getPlayersNickname().get(winner);
        }

        EndOfMatchMessage finalMessage = new EndOfMatchMessage(winner, winnerNickname, reason);
        controller.sendMessageAsBroadcast(finalMessage);

        // set to true the attribute matchEnded inside the Controller
        controller.setMatchEnded(true);
    }

    /**
     * This method finds which player is the winner of the specified match
     * @param controller reference to the controller of the match
     * @return ID of the winner or -1 if there is a tie
     */
    static int computeWinner(Controller controller){

        Match match = controller.getMatch();

        Player tempWinner = null;
        int winnerTowers = 100;
        int tempTowersOfPlayer;

        int winnerProfessors;
        int tempPlayerProfessors;
        boolean tie = false;

        for(Player p: match.getPlayers()){
            // we consider the player as possible winner only if he's still connected
            if(!controller.getPlayersDisconnected().get(p.getID())){
                tempTowersOfPlayer = p.getSchoolBoard().getTowerArea().getCurrentNumberOfTowers();

                if(tempTowersOfPlayer < winnerTowers || tempWinner == null){
                    winnerTowers = tempTowersOfPlayer;
                    tempWinner = p;
                    tie = false;
                }else if(tempTowersOfPlayer == winnerTowers){

                    winnerProfessors = tempWinner.getMyProfessors().size();
                    tempPlayerProfessors = p.getMyProfessors().size();

                    if(tempPlayerProfessors > winnerProfessors){
                        /* N.B. We don't need to assign tempTowersOfPlayer to winnerTowers
                         * because they are already the same value (in fact we are inside the else if(...)) */
                        tempWinner = p;
                        tie = false;
                    }else if(tempPlayerProfessors == winnerProfessors){
                        tie = true;
                    }
                }
            }
        }

        assert tempWinner != null;
        return tie ? -1 : tempWinner.getID();
    }

    /**
     * This method finds which player is currently controlling the professor given as argument
     * @param match reference to the match (model)
     * @param creature the kind of professor we are interested in
     * @return ID of the player controlling the professor or -1 if no player controls it
     */
    static public int whoControlsTheProfessor(Match match, Creature creature){
        int player_ID = -1;

        for(int i = 0; i < match.getPlayers().size(); i++){

            if(match.getPlayers().get(i).getSchoolBoard().getProfessorTable().isOccupied(creature)){
                assert player_ID == -1 : "ACTION_1 controller state:\ntwo players simultaneously " +
                        "controlling the" + creature + "professor";
                player_ID = i;
            }
        }

        // if nobody controls the creature it should be in the notControlledProfessors list
        assert player_ID != -1 || match.getNotControlledProfessors().contains(creature) :
                "The creature is not yet controlled but it is not inside the notControlledProfessors attribute" +
                        "defined in class Match";

        return player_ID;
    }

    /**
     * This method computes the influence of all players on the island and returns the player who is the
     * master of the island
     * @param controller reference to the controller of the match
     * @param island_ID the ID of the island whose influence we are interested in
     * @return ID of the player that has the higher influence on the island (also if the master did not change)
     *         or if the there are two players with the same influence value, which is the maximum
     *         value, then -1 is returned
     */
    static public int influenceComputation(Controller controller, int island_ID){
        Match match = controller.getMatch();

        // in this variable we store for each player the influence on the island : HashMap<player_ID, influence>
        HashMap<Integer, Integer> allPlayersInfluence = new HashMap<>();

        Archipelago island = match.getRealmOfTheMatch().getArchipelagos().get(island_ID);
        ArrayList<Creature> playerProfessors;

        for(int player_ID = 0; player_ID < match.getPlayers().size(); player_ID++){
            Player player = match.getPlayers().get(player_ID);
            playerProfessors = player.getMyProfessors();

            // count the number of students of each type on the island
            int playerInfluence = 0;
            for(Creature creature: playerProfessors){

                // control if mushroom-merchant has been used
                if(controller.isExpertMode() && controller.getCharactersManager().isMushroomsMerchantActive()){
                    MushroomsMerchant mushroomsMerchant = (MushroomsMerchant) controller.getCharactersManager().getCards().get("mushroomMerchant");
                    /* we count the number of students only if they are not of the type
                    to which the effect of the mushrooms-merchant card is applied*/
                    if(!(mushroomsMerchant.getCreatureChosen().equals(creature))){
                        playerInfluence += island.getStudentsOfType(creature);
                    }
                }else{
                    playerInfluence += island.getStudentsOfType(creature);
                }
            }

            if(!controller.isExpertMode() || !controller.getCharactersManager().isCentaurActive()){
                // if the players owns the tower(s) then we also count them in the influence
                playerInfluence += match.numberOfTowersOnTheIsland(player_ID, island_ID);
            }

            // if the player is using the knight character two points are added to the influence
            if(controller.isExpertMode() && controller.getCharactersManager().getKnightUser() == player_ID){
                allPlayersInfluence.put(player_ID, playerInfluence + 2);
                // RESET knightUser
                controller.getCharactersManager().setKnightUser(-1);
            }else{
                allPlayersInfluence.put(player_ID, playerInfluence);
            }
        }
        // RESET centaur character card
        if(controller.isExpertMode()){
            controller.getCharactersManager().setCentaurActive(false);
        }

        // control who has the higher influence
        int maxInfluence = 0;
        int playerWithMaxInfluence = -1;
        int equalInfluenceCounter = 0;

        for(int i = 0; i < match.getPlayers().size(); i++){
            if(allPlayersInfluence.get(i) > maxInfluence){
                equalInfluenceCounter = 1;
                playerWithMaxInfluence = i;
                maxInfluence = allPlayersInfluence.get(i);
            }else if(allPlayersInfluence.get(i) == maxInfluence){
                equalInfluenceCounter++;
            }
        }

        /* if we found a max value more than once then the influence is not valid, meaning no one is getting the
        control over the island */
        if(equalInfluenceCounter > 1){
            return -1;
        }else {
            return playerWithMaxInfluence;
        }
    }

    /**
     * This method change the status of one professor in the players' tables,
     * checking the previous situation and the current one after some action occurred,
     * for example when a character card is used (bard, princess or trafficker)
     * @param controller reference to the controller of th match
     * @param previousOwner_ID the ID of the previous owner of the professor
     * @param creature type of professor considered
     */
    static public void updateProfessorControl(Controller controller, int previousOwner_ID, Creature creature){
        Match match = controller.getMatch();
        int maxStudentAtTheTable = 0;
        Player previousOwner = null;

        if(previousOwner_ID != -1){
            previousOwner = match.getPlayerByID(previousOwner_ID);
            maxStudentAtTheTable = previousOwner.getSchoolBoard().getDiningRoom().getOccupiedSeatsAtTable(creature);
        }

        Player tempPlayer;
        int studentsOfTempPlayer;

        int currentOwner_ID = previousOwner_ID;

        // find who is currently controlling the professor
        for(int i = 0; i < match.getNumberOfPlayers(); i++){
            tempPlayer = match.getPlayerByID(i);
            studentsOfTempPlayer = tempPlayer.getSchoolBoard().getDiningRoom().getOccupiedSeatsAtTable(creature);

            if(studentsOfTempPlayer > maxStudentAtTheTable){
                currentOwner_ID = i;
                maxStudentAtTheTable = studentsOfTempPlayer;
            }
        }

        // no one controlled the professor but now one player does
        if(previousOwner_ID == -1 && currentOwner_ID != previousOwner_ID){
            match.getPlayerByID(currentOwner_ID).getSchoolBoard().getProfessorTable().addProfessor(creature);
        }
        // the player owning the professor has changed
        else if(previousOwner_ID != -1 && currentOwner_ID != previousOwner_ID){
            match.getPlayerByID(previousOwner_ID).getSchoolBoard().getProfessorTable().removeProfessor(creature);
            match.getPlayerByID(currentOwner_ID).getSchoolBoard().getProfessorTable().addProfessor(creature);
        }

        /* if the previous owner has no more students on the table we remove the professor
        * nobody controls this professor for now*/
        if(previousOwner_ID == currentOwner_ID && previousOwner_ID != -1 && previousOwner.getSchoolBoard().getDiningRoom().getOccupiedSeatsAtTable(creature) == 0){
            match.getPlayerByID(previousOwner_ID).getSchoolBoard().getProfessorTable().removeProfessor(creature);
            match.getNotControlledProfessors().add(creature);
        }
    }

}
