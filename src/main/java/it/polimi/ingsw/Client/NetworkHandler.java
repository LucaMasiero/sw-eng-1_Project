package it.polimi.ingsw.Client;


import com.google.gson.Gson;
import it.polimi.ingsw.messages.Message;
import it.polimi.ingsw.messages.clientMessages.*;
import it.polimi.ingsw.messages.serverMessages.*;
import it.polimi.ingsw.model.Creature;
import it.polimi.ingsw.model.Tower;
import it.polimi.ingsw.model.Wizard;
import it.polimi.ingsw.messages.clientMessages.PingMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * The class handles the client-side connection between the server and the client
 */
public class NetworkHandler {
    private CLI cli;
    private String nickNamePlayer;
    private int playerID;
    private Socket clientSocket = null;
    private final Gson gsonObj = new Gson();
    private BufferedReader inputBufferClient = null;
    private PrintWriter outputPrintClient = null;
    private String ip;
    private int port;

    /**
     * This attribute represents the personal modelView of a player, and it is created in the first
     * update method, after receiving the match start message.
     */
    private ModelView modelView;


    /**
     * This attribute tracks the moment, during a round, when a character card is used, so that
     * the player can continue his turn after resolving a character card.
     */
    private String lastCallFrom = null;

    /**
     * This attribute tells if the match is over, which means an EndOfMatchMessage has been received from the server.
     */
    private boolean matchEnd = false;

    /**
     * This attribute tells if the match has started.
     */
    private boolean matchStarted = false;

    /**
     * This attribute is used to understand if the player has already chosen his tower or not. If so,
     * we can move on to the deck choice.
     */
    private Tower towerColor;

    /**
     * This attribute is used to understand if the player has already chosen his tower or not. If so,
     * we can move on to the assistant choice.
     */
    private Wizard wizard;


    /**
     * We use this flag to understand if the player has already used the assistant card. If so,
     * the action phase can begin.
     */
    private boolean assistantChoiceFlag = false;
    /**
     * We use this attribute to track the number of student the player has moved from the entrance.
     */
    private int numberOfChosenStudent = 0;

    /**
     * This attribute indicates the student the player decided to move from the entrance in the action_1.
     */
    private int studentChosen;

    /**
     * This attribute tracks the position of mother nature in the game.
     */
    int motherNatureIslandID;

    /**
     * We use this attribute to know the maximum number of students that can be moved from the entrance.
     * It can be 3, if there are 2 players playing, or 4 if there are 3 players playing.
     */
    private int numberOfStudentToMoveAction1;

    /**
     * This attribute tracks the rounds while the messenger character card is active.
     */
    private boolean messengerActive = false;

    /**
     * This attribute indicates the number of students that have been moved from the entrance after using
     * the Jester character card.
     */
    private int jesterNumber = 0;

    /**
     * This attribute indicates the number of students that have been moved from the entrance after using
     * the Bard character card.
     */
    private int bardNumber = 0;

    /**
     * This attribute tells if a character has been used during a certain round.
     */
    private boolean characterUsed = false;

    /**
     * This attribute tells if the students in the bag are ended, which means there won't be an action3.
     */
    private boolean action3valid = true;

    /**
     * This attributes tracks the following player when the action3Valid attribute is true.
     */
    private int nextPlayerAction3NotValid;
    /**
     * NetworkHandler constructor which creates a new instance of the NetworkHandler.
     *
     * @param ipReceived   is the server ip.
     * @param portReceived is the server port.
     * @param cliReceived  is a reference to the cli.
     */
    public NetworkHandler(String ipReceived, int portReceived, CLI cliReceived) {
        this.ip = ipReceived;
        this.port = portReceived;
        this.cli = cliReceived;
    }


    /**
     * This method starts the communication between the client (NetworkHandler) and the server.
     * It initializes the socket, the input and output buffer and launches the login part through the loginFromClient method.
     */
    public void startClient() {
        try {
            clientSocket = new Socket(ip, port);
            inputBufferClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            outputPrintClient = new PrintWriter(clientSocket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
            closingNH();
            System.exit(0);
        }
        keepAlive();
        loginFromClient();

        try {
            while (!matchEnd) {
                //System.out.println("Still connected");
                String msgFromServer = inputBufferClient.readLine();
                //System.out.println("messaggio dal server: " + msgFromServer);
                analysisOfReceivedMessageServer(msgFromServer);
            }
        } catch (IOException e) {
            System.out.println("Server no longer available :(  " + e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        closingNH();
        System.exit(0);
    }

    /**
     * This method starts a thread which sends a Ping message to the server every 5 seconds
     */
    private void keepAlive(){
        new Thread(() -> {
            while(true){
                try {
                    TimeUnit.MILLISECONDS.sleep(5000);
                    sendMessage(new PingMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }
    /**
     * This method serializes the client message in json and sends it to the server.
     * @param msgToSend is the message to be sent.
     */
    public void sendMessage(Message msgToSend) {
        outputPrintClient.println(gsonObj.toJson(msgToSend));
        outputPrintClient.flush();
    }

    /**
     * This method is used by the NetworkHandler, which represent the client-side of the game, to access the game:
     * nickNamePlayer represents the nickName chosen.
     * newMatchBool specifies whether the player wants to create a new match or not.
     */
    public void loginFromClient() {
        nickNamePlayer = cli.loginNickname();
        boolean newMatchBool = cli.newMatchBoolean();
        LoginMessage msgLogin = new LoginMessage(nickNamePlayer, newMatchBool);
        sendMessage(msgLogin);
    }


    /**
     * This method is used by the NetworkHandler class to analyse the received message from the server.
     * @param receivedMessageInJson is the string received in json format, which will be deserialized.
     */
    public void analysisOfReceivedMessageServer(String receivedMessageInJson) throws InterruptedException {

        Message receivedMessageFromJson = gsonObj.fromJson(receivedMessageInJson, Message.class);

        String messageObject = receivedMessageFromJson.getObjectOfMessage();


        // analyze the message
        switch (messageObject) {

            /* DEFAULT TO USE:
                LoginMessage msgLogin = new LoginMessage();
                msgLogin = gsonObj.fromJson(receivedMessageInJson, LoginMessage.class);
                System.out.println(msgLogin.getNicknameOfPlayer());
                loginInServer(msgLogin.getNicknameOfPlayer());
                break;
                */
            case "MatchCreation":
                cli.loginSuccess();
                AckMatchCreationMessage msgLoginSuccess = gsonObj.fromJson(receivedMessageInJson, AckMatchCreationMessage.class);
                boolean newMatchNeeded = msgLoginSuccess.getNewMatchNeeded();
                playerID = msgLoginSuccess.getPlayerID();
                if (newMatchNeeded) {
                    creatingNewSpecsFromClient();
                } else {
                    sendAckFromClient();
                }
                break;

            case "join match":
                AskMatchToJoinMessage askMatchToJoinMessage = gsonObj.fromJson(receivedMessageInJson, AskMatchToJoinMessage.class);

                int lobbyIDChosenByPlayer = cli.lobbyToChoose(askMatchToJoinMessage.getLobbiesTmp(), askMatchToJoinMessage.getLobbiesExpertMode(), askMatchToJoinMessage.getLobbiesNumberOfPlayers(), askMatchToJoinMessage.getLobbiesEnd());

                ReplyChosenLobbyToJoinMessage replyChosenLobbyToJoinMessage = new ReplyChosenLobbyToJoinMessage(lobbyIDChosenByPlayer);
                sendMessage(replyChosenLobbyToJoinMessage);

                break;

            case "playerID_set":
                IDSetAfterLobbyChoiceMessage idSetAfterLobbyChoiceMessage = gsonObj.fromJson(receivedMessageInJson, IDSetAfterLobbyChoiceMessage.class);
                playerID = idSetAfterLobbyChoiceMessage.getPlayerID();
                System.out.println("player id: " + playerID);
                break;

            case "NicknameNotValid":
                cli.nicknameNotAvailable();
                loginFromClient();
                break;

            case "start":
                this.matchStarted = true;
                cli.startAlert();
                modelView = new ModelView(playerID);
                MatchStartMessage matchStartMessage;
                matchStartMessage = gsonObj.fromJson(receivedMessageInJson, MatchStartMessage.class);

                System.out.println("NUMERO DI GIOCATORI TOTALI: " + matchStartMessage.getNumPlayer());
                System.out.println("PARTITA IN EXPERT MODE: " + matchStartMessage.isExpertMode());

                System.out.println("player id: " + playerID);
                System.out.println("first id: " + matchStartMessage.getFirstPlayer());

                updateStartModelView(matchStartMessage);                                            //primo update della cli, gli passo il messaggio ricevuto dal server così posso inizializzare


                if (matchStartMessage.getFirstPlayer() == playerID) {
                    cli.isYourTurn();

                    towerColor = cli.towerChoice(modelView);

                    ChosenTowerColorMessage chosenTowerColorMessage = new ChosenTowerColorMessage();
                    chosenTowerColorMessage.setColor(towerColor);
                    chosenTowerColorMessage.setSender_ID(playerID);
                    sendMessage(chosenTowerColorMessage);


                } else if (matchStartMessage.getFirstPlayer() != playerID){
                    cli.turnWaitingTowers(matchStartMessage.getFirstPlayer());

                }
                break;
            case "end":
                matchIsEnded(receivedMessageInJson);
                break;

            // in case the message was an AckMessage
            case "ack":
                AckMessage ackMessageMapped = gsonObj.fromJson(receivedMessageInJson, AckMessage.class);
                switch (ackMessageMapped.getSubObject()) {
                    case "waiting":
                        cli.ackWaiting();
                        break;

                    case "tower_color":
                        if ((ackMessageMapped.getNextPlayer() == playerID) && (towerColor == null)) {
                            ArrayList<Tower> notAvailableTowerColors = ackMessageMapped.getNotAvailableTowerColors();
                            towerColor = cli.towerChoiceNext(notAvailableTowerColors, modelView);

                            ChosenTowerColorMessage chosenTowerColorMessage = new ChosenTowerColorMessage();
                            chosenTowerColorMessage.setColor(towerColor);
                            chosenTowerColorMessage.setSender_ID(playerID);
                            sendMessage(chosenTowerColorMessage);

                            break;

                        } else if ((ackMessageMapped.getNextPlayer() == playerID) && (towerColor != null)) {
                            wizard = cli.deckChoice();
                            ChosenDeckMessage chosenDeckMessage = new ChosenDeckMessage();
                            chosenDeckMessage.setDeck(wizard);
                            chosenDeckMessage.setSender_ID(playerID);
                            sendMessage(chosenDeckMessage);
                            break;

                        } else if ((ackMessageMapped.getNextPlayer() != playerID) && (towerColor != null)) {
                            if(modelView.getNumberOfPlayersGame() == 3){
                                if(ackMessageMapped.getNotAvailableTowerColors().size() != 3){
                                    cli.turnWaitingTowers(ackMessageMapped.getNextPlayer());
                                }else{
                                    cli.turnWaitingDecks(ackMessageMapped.getNextPlayer());
                                }
                            }else{
                                if(ackMessageMapped.getNotAvailableTowerColors().size() != 2){
                                    cli.turnWaitingTowers(ackMessageMapped.getNextPlayer());
                                }else{
                                    cli.turnWaitingDecks(ackMessageMapped.getNextPlayer());
                                }
                            }
                            break;

                        } else if ((ackMessageMapped.getNextPlayer() != playerID) && (towerColor == null)) {
                            cli.turnWaitingTowers(ackMessageMapped.getNextPlayer());
                            break;

                        }
                        break;

                    case "deck":
                        if ((ackMessageMapped.getNextPlayer() == playerID) && (wizard == null)) {
                            ArrayList<Wizard> notAvailableDecks = ackMessageMapped.getNotAvailableDecks();

                            wizard = cli.deckChoiceNext(notAvailableDecks);
                            ChosenDeckMessage chosenDeckMessage = new ChosenDeckMessage();
                            chosenDeckMessage.setDeck(wizard);
                            chosenDeckMessage.setSender_ID(playerID);
                            sendMessage(chosenDeckMessage);
                            break;

                        } else if ((ackMessageMapped.getNextPlayer() == playerID) && (wizard != null)) {
                            cli.bagClick();
                            sendBagClickedByFirstClient();
                            break;

                        } else if (ackMessageMapped.getNextPlayer() != playerID && (wizard != null)) {
                            if(modelView.getNumberOfPlayersGame() == 3){
                                if(ackMessageMapped.getNotAvailableDecks().size() != 3){
                                    cli.turnWaitingDecks(ackMessageMapped.getNextPlayer());
                                }else{
                                    cli.turnWaiting(ackMessageMapped.getNextPlayer());
                                }
                            }else{
                                if(ackMessageMapped.getNotAvailableDecks().size() != 2){
                                    cli.turnWaitingDecks(ackMessageMapped.getNextPlayer());
                                }else{
                                    cli.turnWaiting(ackMessageMapped.getNextPlayer());
                                }
                            }
                            break;

                        } else if (ackMessageMapped.getNextPlayer() != playerID && (wizard == null)) {
                            cli.turnWaitingDecks(ackMessageMapped.getNextPlayer());
                            break;
                        }

                        break;

                    case "refillClouds":
                        modelView.setStudentsOnClouds(ackMessageMapped.getStudents());

                        cli.showSchoolBoard(playerID, modelView);
                        cli.showCharacterCardsInTheGame(modelView);
                        cli.showIslandsSituation(modelView);
                        cli.showClouds(modelView);

                        if (ackMessageMapped.getNextPlayer() == playerID && !assistantChoiceFlag) {
                            int assistantChosen = cli.assistantChoice(modelView.getAssistantCardsValuesPlayer());
                            modelView.setLastAssistantChosen(assistantChosen);

                            assistantChoiceFlag = true;
                            sendChosenAssistantCardMessage(assistantChosen);

                        } else if (ackMessageMapped.getNextPlayer() != playerID && !assistantChoiceFlag) {
                            cli.turnWaitingAssistant(ackMessageMapped.getNextPlayer());
                        }

                        break;

                    case "assistant":
                        if (ackMessageMapped.getNextPlayer() == playerID && !assistantChoiceFlag) {
                            ArrayList<Integer> assistantAlreadyUsedInThisRound = ackMessageMapped.getAssistantAlreadyUsedInThisRound();
                            int assistantChosen = cli.assistantChoiceNext(modelView.getAssistantCardsValuesPlayer(), assistantAlreadyUsedInThisRound);

                            modelView.setLastAssistantChosen(assistantChosen);

                            assistantChoiceFlag = true;
                            sendChosenAssistantCardMessage(assistantChosen);

                        } else if (ackMessageMapped.getNextPlayer() != playerID && !assistantChoiceFlag) {
                            cli.turnWaitingAssistant(ackMessageMapped.getNextPlayer());

                        } else if (ackMessageMapped.getNextPlayer() != playerID && assistantChoiceFlag) {
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());

                        } else if (ackMessageMapped.getNextPlayer() == playerID && assistantChoiceFlag) {
                            assistantChoiceFlag = false;
                            // choose the student to move
                            studentChosen = cli.choiceOfStudentsToMove(playerID, modelView);
                            if(studentChosen == -2){
                                lastCallFrom = "choiceOfStudentsToMove";
                                String characterChosen = cli.characterChoice(modelView);
                                sendRequestCharacterMessage(characterChosen);
                                break;
                            }
                            // choose where to move the student
                            int locationChosen = cli.choiceLocationToMove(playerID, modelView);
                            if(locationChosen == -2){
                                // if the player chose to use a character
                                lastCallFrom = "choiceLocationToMove";
                                String characterChosen = cli.characterChoice(modelView);
                                sendRequestCharacterMessage(characterChosen);
                                break;
                            }
                            sendMovedStudentsFromEntrance(studentChosen, locationChosen);
                            numberOfChosenStudent ++;

                        }

                        break;

                    case "action_1_dining_room":
                        updateModelViewActionOne(ackMessageMapped);

                        if (ackMessageMapped.getNextPlayer() == playerID && numberOfChosenStudent < numberOfStudentToMoveAction1) {
                            studentChosen = cli.choiceOfStudentsToMove(playerID, modelView);
                            if(studentChosen == -2){
                                lastCallFrom = "choiceOfStudentsToMove";
                                String characterChosen = cli.characterChoice(modelView);
                                sendRequestCharacterMessage(characterChosen);
                                break;
                            }
                            int locationChosen = cli.choiceLocationToMove(playerID, modelView);
                            if(locationChosen == -2){
                                lastCallFrom = "choiceLocationToMove";
                                String characterChosen = cli.characterChoice(modelView);
                                sendRequestCharacterMessage(characterChosen);
                                break;
                            }
                            sendMovedStudentsFromEntrance(studentChosen, locationChosen);
                            numberOfChosenStudent++;
                        } else if (ackMessageMapped.getNextPlayer() != playerID && numberOfChosenStudent <= numberOfStudentToMoveAction1 ) {
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        } else if (ackMessageMapped.getNextPlayer() == playerID && numberOfChosenStudent == numberOfStudentToMoveAction1) {
                            motherNatureIslandID = 0;
                            for (int i = 0; i < 12; i++) {
                                if(modelView.getIslandGame().get(i) != null) {
                                    if (modelView.getIslandGame().get(i).isMotherNaturePresence()) {
                                        motherNatureIslandID = i;
                                    }
                                }
                            }
                            int chosenIslandID = cli.choiceMotherNatureMovement(playerID, motherNatureIslandID, modelView);
                            if(chosenIslandID == -2){
                                lastCallFrom = "choiceMotherNatureMovement";
                                String characterChosen = cli.characterChoice(modelView);
                                sendRequestCharacterMessage(characterChosen);
                                break;
                            }
                            sendMovedMotherNature(chosenIslandID);
                        }

                        break;

                    case "action_1_island":
                        updateModelViewActionOne(ackMessageMapped);
                        if (ackMessageMapped.getNextPlayer() == playerID && numberOfChosenStudent < numberOfStudentToMoveAction1) {
                            studentChosen = cli.choiceOfStudentsToMove(playerID, modelView);
                            if(studentChosen == -2){
                                lastCallFrom = "choiceOfStudentsToMove";
                                String characterChosen = cli.characterChoice(modelView);
                                sendRequestCharacterMessage(characterChosen);
                                break;
                            }
                            int locationChosen = cli.choiceLocationToMove(playerID, modelView);
                            if(locationChosen == -2){
                                lastCallFrom = "choiceLocationToMove";
                                String characterChosen = cli.characterChoice(modelView);
                                sendRequestCharacterMessage(characterChosen);
                                break;
                            }
                            sendMovedStudentsFromEntrance(studentChosen, locationChosen);
                            numberOfChosenStudent++;
                        } else if (ackMessageMapped.getNextPlayer() != playerID && numberOfChosenStudent <= numberOfStudentToMoveAction1) {
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());

                        } else if (ackMessageMapped.getNextPlayer() == playerID && numberOfChosenStudent == numberOfStudentToMoveAction1) {
                            motherNatureIslandID = 0;
                            for (int i = 0; i < 12; i++) {
                                if(modelView.getIslandGame().get(i) != null) {
                                    if (modelView.getIslandGame().get(i).isMotherNaturePresence()) {
                                        motherNatureIslandID = i;
                                    }
                                }
                            }
                            int chosenIslandID = cli.choiceMotherNatureMovement(playerID, motherNatureIslandID, modelView);
                            if(chosenIslandID == -2){
                                lastCallFrom = "choiceMotherNatureMovement";
                                String characterChosen = cli.characterChoice(modelView);
                                sendRequestCharacterMessage(characterChosen);
                                break;
                            }
                            sendMovedMotherNature(chosenIslandID);
                        }
                        break;

                    case "action_2_movement":
                        if(!ackMessageMapped.isAction3Valid()){
                            action3valid = false;
                            nextPlayerAction3NotValid = ackMessageMapped.getNextPlayer();
                        }
                        updateModelViewActionTwo(ackMessageMapped);
                        cli.newMotherNaturePosition(ackMessageMapped.getDestinationIsland_ID());
                        break;

                    case "action_2_influence":
                        updateModelViewActionTwo(ackMessageMapped);
                        for (int i = 0; i < 12; i++) {
                            if(modelView.getIslandGame().get(i) != null) {
                                if (modelView.getIslandGame().get(i).isMotherNaturePresence()) {
                                    motherNatureIslandID = i;
                                }
                            }
                        }
                        if (ackMessageMapped.isMasterChanged()) {
                            modelView.getIslandGame().get(motherNatureIslandID).setMasterOfArchipelago(ackMessageMapped.getNewMaster_ID());
                            if (ackMessageMapped.getNewMaster_ID() == playerID && ackMessageMapped.getPreviousMaster_ID() != playerID) {
                                cli.newMaster(modelView, playerID);
                            }else if(ackMessageMapped.getNewMaster_ID() != playerID && ackMessageMapped.getPreviousMaster_ID() == playerID){
                                cli.oldMaster(modelView, motherNatureIslandID, playerID);
                            }
                        }
                        break;

                    case "action_2_union":
                        updateModelViewActionTwo(ackMessageMapped);
                        if(!(ackMessageMapped.getIslandsUnified().equals("none"))){
                            int islandUnifiedFlag = -2;
                            if(ackMessageMapped.getIslandsUnified().equals("previous")){
                                islandUnifiedFlag = -1;
                            }else if(ackMessageMapped.getIslandsUnified().equals("next")){
                                islandUnifiedFlag = 1;
                            }else if(ackMessageMapped.getIslandsUnified().equals("both")){
                                islandUnifiedFlag = 0;
                            }
                            cli.showUnion(motherNatureIslandID, islandUnifiedFlag, ackMessageMapped.getIslands_ID());
                        }
                        if(action3valid) {
                            if (ackMessageMapped.getNextPlayer() == playerID) {
                                int cloudChosenID = cli.chooseCloud(playerID, modelView);
                                if (cloudChosenID == -2) {
                                    lastCallFrom = "chooseCloud";
                                    String characterChosen = cli.characterChoice(modelView);
                                    sendRequestCharacterMessage(characterChosen);
                                    break;
                                }
                                sendChosenCloudMessage(cloudChosenID);
                            } else if (ackMessageMapped.getNextPlayer() != playerID) {
                                cli.turnWaitingClouds(ackMessageMapped.getNextPlayer());
                            }
                        }else{
                            if(nextPlayerAction3NotValid == playerID){
                                studentChosen = cli.choiceOfStudentsToMove(playerID, modelView);
                                if(studentChosen == -2){
                                    lastCallFrom = "choiceOfStudentsToMove";
                                    String characterChosen = cli.characterChoice(modelView);
                                    sendRequestCharacterMessage(characterChosen);
                                    break;
                                }
                                int locationChosen = cli.choiceLocationToMove(playerID, modelView);
                                if(locationChosen == -2){
                                    lastCallFrom = "choiceLocationToMove";
                                    String characterChosen = cli.characterChoice(modelView);
                                    sendRequestCharacterMessage(characterChosen);
                                    break;
                                }
                                sendMovedStudentsFromEntrance(studentChosen, locationChosen);
                                numberOfChosenStudent++;
                            }else{
                                cli.turnWaiting(nextPlayerAction3NotValid);
                            }
                        }
                        break;

                    case "action_3":
                        updateModelViewActionThree(ackMessageMapped);
                        messengerActive = false;
                        characterUsed = false;
                        if(ackMessageMapped.getNextPlayer() == playerID && ackMessageMapped.isNextPlanningPhase()){
                            cli.newRoundBeginning();
                            cli.bagClick();
                            sendBagClickedByFirstClient();
                        }else if(ackMessageMapped.getNextPlayer() != playerID && ackMessageMapped.isNextPlanningPhase()){
                            cli.newRoundBeginning();
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }else if(ackMessageMapped.getNextPlayer() != playerID && !ackMessageMapped.isNextPlanningPhase()){
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }else if(ackMessageMapped.getNextPlayer() == playerID && !ackMessageMapped.isNextPlanningPhase()){
                            int studentChosen = cli.choiceOfStudentsToMove(playerID, modelView);
                            if(studentChosen == -2){
                                lastCallFrom = "choiceOfStudentsToMove";
                                String characterChosen = cli.characterChoice(modelView);
                                sendRequestCharacterMessage(characterChosen);
                                break;
                            }
                            int locationChosen = cli.choiceLocationToMove(playerID, modelView);
                            if(locationChosen == -2){
                                lastCallFrom = "choiceLocationToMove";
                                String characterChosen = cli.characterChoice(modelView);
                                sendRequestCharacterMessage(characterChosen);
                                break;
                            }
                            sendMovedStudentsFromEntrance(studentChosen, locationChosen);
                            numberOfChosenStudent++;
                            assistantChoiceFlag = false;
                        }
                        break;
                    case "monk":
                        if (ackMessageMapped.getNextPlayer() == playerID) {
                            int studentChosen = cli.choiceStudentMonk(modelView);
                            int islandChosen = cli.choiceIslandMonk(modelView);
                            sendCharacterDataMonk(studentChosen, islandChosen);
                        }else if (ackMessageMapped.getNextPlayer() != playerID){
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }
                        break;
                    case "cook" :
                        if (ackMessageMapped.getNextPlayer() == playerID) {
                            sendCharacterDataCook();
                        }else if (ackMessageMapped.getNextPlayer() != playerID){
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }

                        break;

                    case "centaur" :
                        if (ackMessageMapped.getNextPlayer() == playerID) {
                            sendCharacterDataCentaur();
                        }else if (ackMessageMapped.getNextPlayer() != playerID){
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }
                        break;
                    case "jester":
                        if (ackMessageMapped.getNextPlayer() == playerID) {
                            ArrayList<Integer> studentsFromEntranceJester = cli.choiceStudentEntranceJester(playerID, modelView);
                            ArrayList<Integer> studentsFromCardJester = cli.choiceStudentCardJester(modelView);
                            sendCharacterDataJester(studentsFromEntranceJester, studentsFromCardJester);

                        }else if (ackMessageMapped.getNextPlayer() != playerID){
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }
                        break;
                    case "knight" :
                        if (ackMessageMapped.getNextPlayer() == playerID) {
                            sendCharacterDataKnight();
                        }else if (ackMessageMapped.getNextPlayer() != playerID){
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }
                        break;

                    case "messenger" :
                        if (ackMessageMapped.getNextPlayer() == playerID) {
                            sendCharacterDataMessenger();
                        } else if (ackMessageMapped.getNextPlayer() != playerID){
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }
                        break;

                    case "herbalist" :
                        if (ackMessageMapped.getNextPlayer() == playerID) {
                            int islandIDChosenHerbalist = cli.choiceHerbalist(modelView);

                            sendCharacterDataHerbalist(islandIDChosenHerbalist);
                        }else if (ackMessageMapped.getNextPlayer() != playerID){
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }
                        break;

                    case "ambassador" :
                        if (ackMessageMapped.getNextPlayer() == playerID) {
                            int islandIDChosenAmbassador = cli.choiceAmbassador(modelView);

                            sendCharacterDataAmbassador(islandIDChosenAmbassador);
                        }else if (ackMessageMapped.getNextPlayer() != playerID){
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }
                        break;

                    case "mushroomMerchant" :
                        if (ackMessageMapped.getNextPlayer() == playerID) {
                            Creature chosenStudentMushroomsMerchant = cli.choiceMushroomsMerchant();

                            sendCharacterDataMushroomsMerchant(chosenStudentMushroomsMerchant);
                        }else if (ackMessageMapped.getNextPlayer() != playerID){
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }
                        break;
                    case "bard":
                        if (ackMessageMapped.getNextPlayer() == playerID) {
                            ArrayList<Integer> studentsFromEntranceBard = cli.choiceStudentEntranceBard(playerID, modelView);
                            ArrayList<Creature> studentsFromDiningRoomBard;
                            if(studentsFromEntranceBard == null){
                                studentsFromDiningRoomBard = null;
                            }else {
                                studentsFromDiningRoomBard = cli.choiceStudentDiningRoomBard(playerID, modelView);
                            }
                            sendCharacterDataBard(studentsFromEntranceBard, studentsFromDiningRoomBard);
                        }else if (ackMessageMapped.getNextPlayer() != playerID){
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }

                        break;
                    case "trafficker" :
                        if (ackMessageMapped.getNextPlayer() == playerID) {
                            Creature chosenStudentTrafficker = cli.choiceTrafficker();
                            sendCharacterDataTrafficker(chosenStudentTrafficker);
                        }else if (ackMessageMapped.getNextPlayer() != playerID) {
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }

                        break;

                    case "princess":
                        if (ackMessageMapped.getNextPlayer() == playerID) {
                            int chosenStudentID = cli.choicePrincess(modelView);

                            sendCharacterDataPrincess(chosenStudentID);
                        }else if (ackMessageMapped.getNextPlayer() != playerID){
                            cli.turnWaiting(ackMessageMapped.getNextPlayer());
                        }
                        break;
                }

                break;

            // in case the message was a Nack
            case "nack":
                NackMessage nackMessageMapped = gsonObj.fromJson(receivedMessageInJson, NackMessage.class);
                switch (nackMessageMapped.getSubObject()) {
                    case "invalid_mother_nature_movement":
                        cli.invalidMotherNatureMovement();


                        for (int i = 0; i < 12; i++) {
                            if(modelView.getIslandGame().get(i) != null) {
                                if (modelView.getIslandGame().get(i).isMotherNaturePresence()) {
                                    motherNatureIslandID = i;
                                }
                            }
                        }
                        int chosenIslandID = cli.choiceMotherNatureMovement(playerID, motherNatureIslandID, modelView);
                        if(chosenIslandID == -2){
                            lastCallFrom = "choiceMotherNatureMovement";
                            String characterChosen = cli.characterChoice(modelView);
                            sendRequestCharacterMessage(characterChosen);
                            break;
                        }
                        sendMovedMotherNature(chosenIslandID);

                        break;

                    case "invalid_cloud":
                        int cloudChosenID = cli.invalidCloudSelection(playerID, modelView);
                        if(cloudChosenID == -2){
                            lastCallFrom = "chooseCloud";
                            String characterChosen = cli.characterChoice(modelView);
                            sendRequestCharacterMessage(characterChosen);
                            break;
                        }
                        sendChosenCloudMessage(cloudChosenID);
                        break;

                    case "herbalist":
                        characterUsed = false;
                        cli.invalidHerbalistChoice(nackMessageMapped.getExplanationMessage());
                        followingChoiceToMake(lastCallFrom);
                        break;

                    case "princess":
                        characterUsed = false;
                        int princessIndex = modelView.getCharacterCardsInTheGame().indexOf("princess");
                        if(modelView.getCharactersPriceIncreased().get(princessIndex)){
                            modelView.getCoinPlayer().replace(playerID, (modelView.getCoinPlayer().get(playerID) - 3) );
                            modelView.setCoinGame(modelView.getCoinGame() + 3);
                        }else{
                            modelView.getCoinPlayer().replace(playerID, (modelView.getCoinPlayer().get(playerID) - 2) );
                            modelView.setCoinGame(modelView.getCoinGame() + 2);
                            modelView.getCharactersPriceIncreased().set(princessIndex, true);
                        }

                        cli.invalidPrincessChoice(nackMessageMapped.getExplanationMessage());
                        followingChoiceToMake(lastCallFrom);
                        break;

                    case "character_price":
                        characterUsed = false;
                        cli.invalidCharacter(nackMessageMapped.getExplanationMessage());
                        followingChoiceToMake(lastCallFrom);
                        break;

                    case "lobby_not_available":
                        matchEnd = true;
                        cli.lobbyChosenNotAvailable(nackMessageMapped.getExplanationMessage());
                        break;

                    case "table_full":
                        numberOfChosenStudent--;
                        cli.invalidStudentMovementTableFull(nackMessageMapped.getExplanationMessage());
                        studentChosen = cli.choiceOfStudentsToMove(playerID, modelView);
                        if(studentChosen == -2){
                            lastCallFrom = "choiceOfStudentsToMove";
                            String characterChosen = cli.characterChoice(modelView);
                            sendRequestCharacterMessage(characterChosen);
                            break;
                        }
                        int locationChosen = cli.choiceLocationToMove(playerID, modelView);
                        if(locationChosen == -2){
                            lastCallFrom = "choiceLocationToMove";
                            String characterChosen = cli.characterChoice(modelView);
                            sendRequestCharacterMessage(characterChosen);
                            break;
                        }
                        sendMovedStudentsFromEntrance(studentChosen, locationChosen);
                        numberOfChosenStudent++;

                        break;
                }
                break;

            case "no lobby available":
                NoLobbyAvailableMessage noLobbyAvailableMessage = gsonObj.fromJson(receivedMessageInJson, NoLobbyAvailableMessage.class);
                playerID = noLobbyAvailableMessage.getPlayerID();
                cli.lobbyNotAvailable();
                creatingNewSpecsFromClient();
                break;

            case "character_ack":
                characterUsed = true;
                AckCharactersMessage ackCharactersMessage = gsonObj.fromJson(receivedMessageInJson, AckCharactersMessage.class);
                if(ackCharactersMessage.getCharacter().equals("messenger")){
                    messengerActive = true;
                }
                //update:
                updateCharacterCard(ackCharactersMessage);
                //callFrom:
                if(ackCharactersMessage.getRecipient() == playerID) {
                    cli.characterUsed(ackCharactersMessage.getCharacter(), ackCharactersMessage.getRecipient(), playerID);
                    followingChoiceToMake(lastCallFrom);
                }else{
                    cli.characterUsed(ackCharactersMessage.getCharacter(), ackCharactersMessage.getRecipient(), playerID);
                    cli.turnWaiting(ackCharactersMessage.getNextPlayer());
                }
                break;

            default:
                cli.errorObject();
        }
    }

    /**
     * This method is used to let the player make the following choice regarding
     * the moment when he chose to use the character card.
     * @param lastCallFrom is the moment when the player uses the character card.
     */
    public void followingChoiceToMake(String lastCallFrom){
        if (lastCallFrom.equals("choiceOfStudentsToMove")) {
            studentChosen = cli.choiceOfStudentsToMove(playerID, modelView);
            if(studentChosen == -2){
                this.lastCallFrom = "choiceOfStudentsToMove";
                String characterChosen = cli.characterChoice(modelView);
                sendRequestCharacterMessage(characterChosen);
            }else {
                int locationChosen = cli.choiceLocationToMove(playerID, modelView);
                if(locationChosen == -2){
                    this.lastCallFrom = "choiceLocationToMove";
                    String characterChosen = cli.characterChoice(modelView);
                    sendRequestCharacterMessage(characterChosen);
                }else {
                    sendMovedStudentsFromEntrance(studentChosen, locationChosen);
                    numberOfChosenStudent++;
                }
            }
        } else if (lastCallFrom.equals("choiceLocationToMove")) {
            int locationChosen = cli.choiceLocationToMove(playerID, modelView);
            if(locationChosen == -2){
                this.lastCallFrom = "choiceLocationToMove";
                String characterChosen = cli.characterChoice(modelView);
                sendRequestCharacterMessage(characterChosen);
            }else {
                sendMovedStudentsFromEntrance(studentChosen, locationChosen);
                numberOfChosenStudent++;
            }
        } else if (lastCallFrom.equals("choiceMotherNatureMovement")) {
            int chosenIslandID = cli.choiceMotherNatureMovement(playerID, motherNatureIslandID, modelView);
            if(chosenIslandID == -2) {
                this.lastCallFrom = "choiceMotherNatureMovement";
                String characterChosen = cli.characterChoice(modelView);
                sendRequestCharacterMessage(characterChosen);
            }else {
                sendMovedMotherNature(chosenIslandID);
            }
        }else if(lastCallFrom.equals("chooseCloud")){
            int cloudChosenID = cli.chooseCloud(playerID, modelView);
            if(cloudChosenID == -2){
                this.lastCallFrom = "chooseCloud";
                String characterChosen = cli.characterChoice(modelView);
                sendRequestCharacterMessage(characterChosen);
            }else {
                sendChosenCloudMessage(cloudChosenID);
            }
        }
    }
    /**
     * This method creates an Ack message and sends it to the server.
     */
    private void sendAckFromClient() {
        AckMessage ackMessage = new AckMessage();
        sendMessage(ackMessage);
    }


    /**
     * This method is used by the client after receiving a loginSuccess message from the server;
     * It creates a MatchSpecsMessage message with the number of players and the variant (expert mode) chosen by the player
     */
    public void creatingNewSpecsFromClient() {
        MatchSpecsMessage newMatchSpecsMessage;

        int numberOfPlayerInTheLobby = cli.numberOfPlayer();
        boolean expertMode = cli.expertModeSelection();

        newMatchSpecsMessage = new MatchSpecsMessage(numberOfPlayerInTheLobby, expertMode);

        sendMessage(newMatchSpecsMessage);
    }


    /**
     * This method creates a new  BagClick message and sends it to the server.
     */
    public void sendBagClickedByFirstClient() {
        BagClickMessage bagClickMessage = new BagClickMessage();
        bagClickMessage.setSender_ID(playerID);
        sendMessage(bagClickMessage);
    }

    /**
     * This method creates a new ChosenAssistantCard message and sends it to the server.
     * @param assistantChosen is the assistant chosen.
     */
    public void sendChosenAssistantCardMessage(int assistantChosen) {
        ChosenAssistantCardMessage chosenAssistantCardMessage = new ChosenAssistantCardMessage(assistantChosen);
        chosenAssistantCardMessage.setSender_ID(playerID);
        sendMessage(chosenAssistantCardMessage);
    }

    /**
     * This method creates a new MovedStudentsFromEntrance message and sends it to the server.
     * @param studentChosen  is the student chosen.
     * @param locationChosen is the location, island or dining room, chosen.
     */
    public void sendMovedStudentsFromEntrance(int studentChosen, int locationChosen) {
        MovedStudentsFromEntranceMessage movedStudentsFromEntranceMessage = new MovedStudentsFromEntranceMessage();
        movedStudentsFromEntranceMessage.setStudent_ID(studentChosen);
        movedStudentsFromEntranceMessage.setLocation(locationChosen);
        movedStudentsFromEntranceMessage.setSender_ID(playerID);

        sendMessage(movedStudentsFromEntranceMessage);
    }

    /**
     * This method creates a new MovedMotherNatureMessage and sends it to the server.
     * @param destinationIsland_ID is the destination Island where to move Mother Nature.
     */
    public void sendMovedMotherNature(int destinationIsland_ID) {
        MovedMotherNatureMessage movedMotherNatureMessage = new MovedMotherNatureMessage(playerID);
        movedMotherNatureMessage.setDestinationIsland_ID(destinationIsland_ID);
        movedMotherNatureMessage.setSender_ID(playerID);
        sendMessage(movedMotherNatureMessage);
    }

    /**
     * This method is used to create and send the Chosen Cloud Message to the server.
     * @param  cloudChosen ID of the cloud chosen by the player
     */
    public void sendChosenCloudMessage(int cloudChosen){
        ChosenCloudMessage chosenCloudMessage = new ChosenCloudMessage(cloudChosen);
        chosenCloudMessage.setSender_ID(playerID);
        sendMessage(chosenCloudMessage);
    }

    /**
     * This method is used to create and send the Chosen Character Message to the server.
     * @param characterChosen  name of the character that the player chose to use
     */
    public void sendRequestCharacterMessage(String characterChosen){
        CharacterRequestMessage characterRequestMessage = new CharacterRequestMessage(playerID, characterChosen);
        sendMessage(characterRequestMessage);
    }

    /**
     * This method is used to send character data for Monk character card.
     */
    public void sendCharacterDataMonk(int studentChosen, int islandChosen){
        CharacterDataMessage characterDataMessage = new CharacterDataMessage(playerID, "monk");
        characterDataMessage.setStudent_ID(studentChosen);
        characterDataMessage.setIsland_ID(islandChosen);
        sendMessage(characterDataMessage);
    }
    /**
     * This method is used to send character data for Cook character card.
     */
    public void sendCharacterDataCook(){
        CharacterDataMessage characterDataMessage = new CharacterDataMessage(playerID, "cook");
        sendMessage(characterDataMessage);
    }

    /**
     * This method is used to send character data for Centaur character card.
     */
    public void sendCharacterDataCentaur(){
        CharacterDataMessage characterDataMessage = new CharacterDataMessage(playerID, "centaur");
        sendMessage(characterDataMessage);
    }

    /**
     * This method is used to send character data for Jester character card.
     * @param studentsFromEntranceJester is the arraylist of students to move the entrance.
     * @param studentsFromCardJester is the arraylist of students to move the Jester card.
     */
    public void sendCharacterDataJester(ArrayList<Integer> studentsFromEntranceJester, ArrayList <Integer> studentsFromCardJester){
        CharacterDataMessage characterDataMessage = new CharacterDataMessage(playerID, "jester");
        characterDataMessage.setStudentsFromPlayerEntrance(studentsFromEntranceJester);
        characterDataMessage.setElementsFromCard(studentsFromCardJester);
        sendMessage(characterDataMessage);
    }

    /**
     * This method is used to send character data for Knight character card.
     */
    public void sendCharacterDataKnight(){
        CharacterDataMessage characterDataMessage = new CharacterDataMessage(playerID, "knight");
        sendMessage(characterDataMessage);
    }

    /**
     * This method is used to send character data for Messenger character card.
     */
    public void sendCharacterDataMessenger(){
        CharacterDataMessage characterDataMessage = new CharacterDataMessage(playerID, "messenger");
        sendMessage(characterDataMessage);
    }

    /**
     * This method is used to send character data for Herbalist character card.
     * @param islandIDChosenHerbalist is the ID of the chosen island where to put the No Entry Tile.
     */
    public void sendCharacterDataHerbalist(int islandIDChosenHerbalist){
        CharacterDataMessage characterDataMessage = new CharacterDataMessage(playerID, "herbalist");
        characterDataMessage.setIsland_ID(islandIDChosenHerbalist);

        sendMessage(characterDataMessage);
    }

    /**
     * This method is used to send character data for ambassador character card.
     * @param islandIDChosenAmbassador is the ID of the chosen island where to compute the influence.
     */
    public void sendCharacterDataAmbassador(int islandIDChosenAmbassador){
        CharacterDataMessage characterDataMessage = new CharacterDataMessage(playerID, "ambassador");
        characterDataMessage.setIsland_ID(islandIDChosenAmbassador);

        sendMessage(characterDataMessage);
    }

    /**
     * This method is used to send character data for Mushrooms Merchant character card.
     * @param chosenStudentMushroomsMerchant is the Creature type of the chosen student by the client.
     */
    public void sendCharacterDataMushroomsMerchant(Creature chosenStudentMushroomsMerchant){
        CharacterDataMessage characterDataMessage = new CharacterDataMessage(playerID, "mushroomsMerchant");
        characterDataMessage.setCreature(chosenStudentMushroomsMerchant);

        sendMessage(characterDataMessage);
    }

    /**
     * This method is used to send character data for Bard character card.
     * @param studentsFromEntranceBard list of students to move from the Entrance to the DiningRoom
     * @param studentsFromDiningRoomBard list of students to move from the DiningRoom to the Entrance
     */
    public void sendCharacterDataBard(ArrayList<Integer> studentsFromEntranceBard, ArrayList <Creature> studentsFromDiningRoomBard ){
        CharacterDataMessage characterDataMessage = new CharacterDataMessage(playerID, "bard");
        characterDataMessage.setStudentsFromPlayerEntrance(studentsFromEntranceBard);
        characterDataMessage.setStudentsFromPlayerDiningRoom(studentsFromDiningRoomBard);
        sendMessage(characterDataMessage);
    }

    /**
     * This method is used to send character data for Trafficker character card.
     * @param chosenStudentTrafficker is the type of student chosen by the client.
     */
    public void sendCharacterDataTrafficker(Creature chosenStudentTrafficker){
        CharacterDataMessage characterDataMessage = new CharacterDataMessage(playerID, "trafficker");
        characterDataMessage.setCreature(chosenStudentTrafficker);

        sendMessage(characterDataMessage);
    }

    /**
     * This method is used to send character data for Princess character card.
     * @param chosenStudentIDPrincess is the ID of the student taken form the character card by the client.
     */
    public void sendCharacterDataPrincess(int chosenStudentIDPrincess){
        CharacterDataMessage characterDataMessage = new CharacterDataMessage(playerID, "princess");
        characterDataMessage.setStudent_ID(chosenStudentIDPrincess);

        sendMessage(characterDataMessage);
    }





    /**
     * This method is used to update the modelView after receiving the matchStartMessage.
     * @param matchStartMessage is the matchStartMatchMessage received.
     */
    public void updateStartModelView(MatchStartMessage matchStartMessage) {
        // set the maximum number of students that can be moved during action_1 for each player
        numberOfStudentToMoveAction1 = matchStartMessage.getNumPlayer() + 1;

        assistantChoiceFlag = false;

        modelView.setNumberOfPlayersGame(matchStartMessage.getNumPlayer());
        modelView.setExpertModeGame(matchStartMessage.isExpertMode());
        if (modelView.isExpertModeGame()) {
            if(matchStartMessage.getNumPlayer() == 2){
                modelView.setCoinGame(18);
            }else if(matchStartMessage.getNumPlayer() == 3){
                modelView.setCoinGame(17);
            }

            for (String s : matchStartMessage.getCharacters()) {
                switch (s) {
                    case "monk":
                        modelView.getCharacterCardsInTheGame().add("monk");
                        modelView.getCharactersPrice().add(1);
                        modelView.getCharactersDataView().setMonkStudents(matchStartMessage.getMonkStudents());
                        break;
                    case "cook":
                        modelView.getCharacterCardsInTheGame().add("cook");
                        modelView.getCharactersPrice().add(2);
                        break;
                    case "messenger":
                        modelView.getCharacterCardsInTheGame().add("messenger");
                        modelView.getCharactersPrice().add(1);
                        break;
                    case "herbalist":
                        modelView.getCharacterCardsInTheGame().add("herbalist");
                        modelView.getCharactersPrice().add(2);
                        modelView.getCharactersDataView().setHerbalistNumberOfNoEntryTile(4);
                        break;
                    case "centaur":
                        modelView.getCharacterCardsInTheGame().add("centaur");
                        modelView.getCharactersPrice().add(3);
                        break;
                    case "jester":
                        modelView.getCharacterCardsInTheGame().add("jester");
                        modelView.getCharactersPrice().add(1);
                        modelView.getCharactersDataView().setJesterStudents(matchStartMessage.getJesterStudents());
                        break;
                    case "knight":
                        modelView.getCharacterCardsInTheGame().add("knight");
                        modelView.getCharactersPrice().add(2);
                        break;
                    case "mushroomMerchant":
                        modelView.getCharacterCardsInTheGame().add("mushroomMerchant");
                        modelView.getCharactersPrice().add(3);
                        break;
                    case "bard":
                        modelView.getCharacterCardsInTheGame().add("bard");
                        modelView.getCharactersPrice().add(1);
                        break;
                    case "princess":
                        modelView.getCharacterCardsInTheGame().add("princess");
                        modelView.getCharactersPrice().add(2);
                        modelView.getCharactersDataView().setPrincessStudents(matchStartMessage.getPrincessStudents());
                        break;
                    case "trafficker":
                        modelView.getCharacterCardsInTheGame().add("trafficker");
                        modelView.getCharactersPrice().add(3);
                        break;
                }
            }
        }

        // set initial position of Mother Nature
        modelView.getIslandGame().get(matchStartMessage.getMotherNaturePosition()).setMotherNaturePresence(true);

        // set initial configuration of islands
        int motherNaturePosition = matchStartMessage.getMotherNaturePosition();
        motherNatureIslandID = matchStartMessage.getMotherNaturePosition();
        int j = 1;
        for (Creature c : matchStartMessage.getStudentsOnIslands()) {
            int islandID = motherNaturePosition + j;
            if (j == 6) {
                modelView.getIslandGame().get((islandID + 1) % 12).addStudent(c);
                j += 2;
            } else {
                modelView.getIslandGame().get(islandID % 12).addStudent(c);
                j++;
            }
        }

        // set all players' Entrance
        for (Integer i : matchStartMessage.getStudentsInEntrance().keySet()) {
            ArrayList<Creature> creatureInEntranceAtStart = matchStartMessage.getStudentsInEntrance().get(i);
            modelView.getSchoolBoardPlayers().put(i, new SchoolBoardView(modelView, matchStartMessage.getNumPlayer()));
            modelView.getSchoolBoardPlayers().get(i).getEntrancePlayer().setStudentsInTheEntrancePlayer(creatureInEntranceAtStart);
        }


    }

    /**
     * This method is used to update the modelView after receiving the action_1 ack message.
     * @param ackMessageMapped is the ack message received.
     */
    public void updateModelViewActionOne(AckMessage ackMessageMapped) {
        assistantChoiceFlag = false;
        if(ackMessageMapped.getPreviousOwnerOfProfessor() != -1 && ackMessageMapped.isProfessorTaken()){
            // set the professor as taken and controlled by a player
            modelView.getSchoolBoardPlayers().get(ackMessageMapped.getPreviousOwnerOfProfessor()).getProfessorTablePlayer().getOccupiedSeatsPlayer().replace(ackMessageMapped.getTypeOfStudentMoved(),false);
        }
        if (ackMessageMapped.getRecipient() == playerID) {
            if (ackMessageMapped.getSubObject().equals("action_1_dining_room")) {
                modelView.getSchoolBoardPlayers().get(playerID).getEntrancePlayer().getStudentsInTheEntrancePlayer().set(ackMessageMapped.getStudentMoved_ID(), null);
                int numberOfStudentOfType = modelView.getSchoolBoardPlayers().get(playerID).getDiningRoomPlayer().getOccupiedSeatsPlayer().get(ackMessageMapped.getTypeOfStudentMoved());
                modelView.getSchoolBoardPlayers().get(playerID).getDiningRoomPlayer().getOccupiedSeatsPlayer().replace(ackMessageMapped.getTypeOfStudentMoved(), (numberOfStudentOfType + 1) );
                if(ackMessageMapped.isProfessorTaken() && !modelView.getSchoolBoardPlayers().get(playerID).getProfessorTablePlayer().getOccupiedSeatsPlayer().get(ackMessageMapped.getTypeOfStudentMoved())) {
                    modelView.getSchoolBoardPlayers().get(playerID).getProfessorTablePlayer().getOccupiedSeatsPlayer().replace(ackMessageMapped.getTypeOfStudentMoved(), ackMessageMapped.isProfessorTaken());
                }

                //UPDATE COINS:
                int module = (modelView.getSchoolBoardPlayers().get(ackMessageMapped.getRecipient()).getDiningRoomPlayer().getOccupiedSeatsPlayer().get(ackMessageMapped.getTypeOfStudentMoved())) % 3;
                if (module == 0) {
                    int newPlayerCoin = modelView.getCoinPlayer().get(ackMessageMapped.getRecipient()) + 1;
                    modelView.getCoinPlayer().replace(ackMessageMapped.getRecipient(), newPlayerCoin);
                    modelView.setCoinGame(modelView.getCoinGame() - 1);
                }

            } else if (ackMessageMapped.getSubObject().equals("action_1_island")) {
                modelView.getSchoolBoardPlayers().get(playerID).getEntrancePlayer().getStudentsInTheEntrancePlayer().set(ackMessageMapped.getStudentMoved_ID(), null);
                modelView.getIslandGame().get(ackMessageMapped.getDestinationIsland_ID()).addStudent(ackMessageMapped.getTypeOfStudentMoved());
            }
        }else if(ackMessageMapped.getRecipient()!= playerID){
            if(ackMessageMapped.getSubObject().equals("action_1_dining_room")){
                // update Entrance
                modelView.getSchoolBoardPlayers().get(ackMessageMapped.getRecipient()).getEntrancePlayer().getStudentsInTheEntrancePlayer().set(ackMessageMapped.getStudentMoved_ID(), null);
                // update DiningRoom
                int numberOfStudentOfType = modelView.getSchoolBoardPlayers().get(ackMessageMapped.getRecipient()).getDiningRoomPlayer().getOccupiedSeatsPlayer().get(ackMessageMapped.getTypeOfStudentMoved());
                modelView.getSchoolBoardPlayers().get(ackMessageMapped.getRecipient()).getDiningRoomPlayer().getOccupiedSeatsPlayer().replace(ackMessageMapped.getTypeOfStudentMoved(), numberOfStudentOfType + 1);

                // update ProfessorTable
                if(ackMessageMapped.isProfessorTaken() && !modelView.getSchoolBoardPlayers().get(ackMessageMapped.getRecipient()).getProfessorTablePlayer().getOccupiedSeatsPlayer().get(ackMessageMapped.getTypeOfStudentMoved())) {
                    modelView.getSchoolBoardPlayers().get(ackMessageMapped.getRecipient()).getProfessorTablePlayer().getOccupiedSeatsPlayer().replace(ackMessageMapped.getTypeOfStudentMoved(), ackMessageMapped.isProfessorTaken());
                }

                //UPDATE COINS:
                int module = (modelView.getSchoolBoardPlayers().get(ackMessageMapped.getRecipient()).getDiningRoomPlayer().getOccupiedSeatsPlayer().get(ackMessageMapped.getTypeOfStudentMoved())) % 3;
                if (module == 0) {
                    int newPlayerCoin = modelView.getCoinPlayer().get(ackMessageMapped.getRecipient()) + 1;
                    modelView.getCoinPlayer().replace(ackMessageMapped.getRecipient(), newPlayerCoin);
                    modelView.setCoinGame(modelView.getCoinGame() - 1);
                }

            }else if (ackMessageMapped.getSubObject().equals("action_1_island")) {
                modelView.getSchoolBoardPlayers().get(ackMessageMapped.getRecipient()).getEntrancePlayer().getStudentsInTheEntrancePlayer().set(ackMessageMapped.getStudentMoved_ID(), null);
                modelView.getIslandGame().get(ackMessageMapped.getDestinationIsland_ID()).addStudent(ackMessageMapped.getTypeOfStudentMoved());
            }
        }
    }

    /**
     * This method is used to update the modelView after receiving the action_2 ack message.
     * @param ackMessageMapped is the ack message received.
     */
    public void updateModelViewActionTwo(AckMessage ackMessageMapped) {
        if (ackMessageMapped.getSubObject().equals("action_2_movement")) {
            if (ackMessageMapped.getRecipient() == playerID) {
                // remove assistant from the deck
                modelView.getAssistantCardsValuesPlayer().remove(modelView.getLastAssistantChosen());
                numberOfChosenStudent = 0;
            }
            // update islands
            for (int i = 0; i < 12; i++) {
                if(modelView.getIslandGame().get(i) != null) {
                    if (modelView.getIslandGame().get(i).isMotherNaturePresence()) {
                        modelView.getIslandGame().get(i).setMotherNaturePresence(false);
                    } else if (i == ackMessageMapped.getDestinationIsland_ID()) {
                        modelView.getIslandGame().get(i).setMotherNaturePresence(true);
                        motherNatureIslandID = i;
                    }
                }
            }
            // remove NoEntryTile
            if (modelView.getIslandGame().get(ackMessageMapped.getDestinationIsland_ID()).getNoEntryTiles() > 0) {
                modelView.getIslandGame().get(ackMessageMapped.getDestinationIsland_ID()).removeNoEntryTile();
            }

        } else if (ackMessageMapped.getSubObject().equals("action_2_influence")) {
            if (ackMessageMapped.isMasterChanged()) {
                if (ackMessageMapped.getNewMaster_ID() != -1) {
                    modelView.getIslandGame().get(motherNatureIslandID).setTowerColor(towerColor);
                    int numberTowerMotherIsland = modelView.getIslandGame().get(motherNatureIslandID).getNumberOfTower();
                    if (numberTowerMotherIsland == 0) {
                        numberTowerMotherIsland++;
                    }
                    //update TowerArea
                    modelView.getIslandGame().get(motherNatureIslandID).setNumberOfTower(numberTowerMotherIsland);
                    int numberCurrentTowerSchoolBoard = modelView.getSchoolBoardPlayers().get(ackMessageMapped.getNewMaster_ID()).getTowerAreaPlayer().getCurrentNumberOfTowersPlayer();
                    modelView.getSchoolBoardPlayers().get(ackMessageMapped.getNewMaster_ID()).getTowerAreaPlayer().setCurrentNumberOfTowersPlayer(numberCurrentTowerSchoolBoard - numberTowerMotherIsland);

                    if(ackMessageMapped.getPreviousMaster_ID() != -1 ) {
                        numberCurrentTowerSchoolBoard = modelView.getSchoolBoardPlayers().get(ackMessageMapped.getPreviousMaster_ID()).getTowerAreaPlayer().getCurrentNumberOfTowersPlayer();
                        modelView.getSchoolBoardPlayers().get(ackMessageMapped.getPreviousMaster_ID()).getTowerAreaPlayer().setCurrentNumberOfTowersPlayer(numberCurrentTowerSchoolBoard + numberTowerMotherIsland);

                    }
                }

            }
        } else if (ackMessageMapped.getSubObject().equals("action_2_union")) {
            if (ackMessageMapped.getIslandsUnified().equals("previous")) {

                //setting current and previous
                int currentIslandID = motherNatureIslandID;
                int previousIslandID = ackMessageMapped.getIslands_ID().get(0);

                //students
                for (Creature c : Creature.values()) {
                    int numberPreviousStudents = modelView.getIslandGame().get(previousIslandID).getStudentsPopulation().get(c);
                    int numberCurrentStudents = modelView.getIslandGame().get(currentIslandID).getStudentsPopulation().get(c);
                    modelView.getIslandGame().get(currentIslandID).getStudentsPopulation().replace(c, numberPreviousStudents + numberCurrentStudents);
                }

                //towers
                int numberTowerPrevious = modelView.getIslandGame().get(previousIslandID).getNumberOfTower();
                int numberTowerCurrent = modelView.getIslandGame().get(currentIslandID).getNumberOfTower();
                if (numberTowerPrevious == 0) {
                    numberTowerPrevious++;
                }
                if (numberTowerCurrent == 0) {
                    numberTowerCurrent++;
                }
                modelView.getIslandGame().get(currentIslandID).setNumberOfTower(numberTowerCurrent + numberTowerPrevious);

                //no entry tile
                if (modelView.getIslandGame().get(currentIslandID).getNoEntryTiles() > 0) {
                    modelView.getIslandGame().get(currentIslandID).removeNoEntryTile();
                    modelView.getCharactersDataView().incrementHerbalistNoEntryTile();

                }

                //removing island
                modelView.getIslandGame().set(previousIslandID, null);

            } else if (ackMessageMapped.getIslandsUnified().equals("next")) {
                //setting current and next
                int currentIslandID = motherNatureIslandID;
                int nextIslandID = ackMessageMapped.getIslands_ID().get(0);

                //students
                for (Creature c : Creature.values()) {
                    // update number of students on the remained island
                    int numberNextStudents = modelView.getIslandGame().get(nextIslandID).getStudentsPopulation().get(c);
                    int numberCurrentStudents = modelView.getIslandGame().get(currentIslandID).getStudentsPopulation().get(c);
                    modelView.getIslandGame().get(currentIslandID).getStudentsPopulation().replace(c, numberNextStudents + numberCurrentStudents);
                }

                //update towers
                int numberTowerNext = modelView.getIslandGame().get(nextIslandID).getNumberOfTower();
                int numberTowerCurrent = modelView.getIslandGame().get(currentIslandID).getNumberOfTower();
                if (numberTowerNext == 0) {
                    numberTowerNext++;
                }
                if (numberTowerCurrent == 0) {
                    numberTowerCurrent++;
                }
                modelView.getIslandGame().get(currentIslandID).setNumberOfTower(numberTowerCurrent + numberTowerNext);

                //update no entry tile
                if (modelView.getIslandGame().get(currentIslandID).getNoEntryTiles() > 0) {
                    modelView.getIslandGame().get(currentIslandID).removeNoEntryTile();
                    modelView.getCharactersDataView().incrementHerbalistNoEntryTile();
                }

                // remove the island unified
                modelView.getIslandGame().set(nextIslandID, null);

            } else if (ackMessageMapped.getIslandsUnified().equals("both")) {
                //setting current, next and previous
                int currentIslandID = motherNatureIslandID;
                int previousIslandID = ackMessageMapped.getIslands_ID().get(0);
                int nextIslandID = ackMessageMapped.getIslands_ID().get(1);

                //students
                for (Creature c : Creature.values()) {
                    int numberPreviousStudents = modelView.getIslandGame().get(previousIslandID).getStudentsPopulation().get(c);
                    int numberNextStudents = modelView.getIslandGame().get(nextIslandID).getStudentsPopulation().get(c);
                    int numberCurrentStudents = modelView.getIslandGame().get(currentIslandID).getStudentsPopulation().get(c);
                    modelView.getIslandGame().get(currentIslandID).getStudentsPopulation().replace(c, numberPreviousStudents + numberNextStudents + numberCurrentStudents);
                }

                //towers
                int numberTowerPrevious = modelView.getIslandGame().get(previousIslandID).getNumberOfTower();
                int numberTowerNext = modelView.getIslandGame().get(nextIslandID).getNumberOfTower();
                int numberTowerCurrent = modelView.getIslandGame().get(currentIslandID).getNumberOfTower();
                if (numberTowerPrevious == 0) {
                    numberTowerPrevious++;
                }
                if (numberTowerNext == 0) {
                    numberTowerNext++;
                }
                if (numberTowerCurrent == 0) {
                    numberTowerCurrent++;
                }
                modelView.getIslandGame().get(currentIslandID).setNumberOfTower(numberTowerCurrent + numberTowerNext + numberTowerPrevious);

                //remove no entry tile
                if (modelView.getIslandGame().get(currentIslandID).getNoEntryTiles() > 0) {
                    modelView.getIslandGame().get(currentIslandID).removeNoEntryTile();
                    modelView.getCharactersDataView().incrementHerbalistNoEntryTile();

                }

                //remove island
                modelView.getIslandGame().set(previousIslandID, null);
                modelView.getIslandGame().set(nextIslandID, null);

            }
        }


    }

    /**
     * This method is used to update the modelView after receiving the action_3 ack message.
     * @param ackMessageMapped is the ack message received.
     */
    public void updateModelViewActionThree(AckMessage ackMessageMapped){
        if(modelView.getNumberOfPlayersGame() == 2){
            if(ackMessageMapped.getCloudChosen_ID() == 0){
                for(int i = 0; i< 3; i++){
                    modelView.getStudentsOnClouds().set(i, null);
                }

            }else if(ackMessageMapped.getCloudChosen_ID() == 1){
                for(int i = 3; i< 6; i++){
                    modelView.getStudentsOnClouds().set(i, null);
                }

            }
        }else if(modelView.getNumberOfPlayersGame() == 3){
            if(ackMessageMapped.getCloudChosen_ID() == 0){
                for(int i = 0; i< 4; i++){
                    modelView.getStudentsOnClouds().set(i, null);
                }

            }else if(ackMessageMapped.getCloudChosen_ID() == 1){
                for(int i = 4; i< 8; i++){
                    modelView.getStudentsOnClouds().set(i, null);
                }

            }else if(ackMessageMapped.getCloudChosen_ID() == 2){
                for(int i = 8; i< 12; i++){
                    modelView.getStudentsOnClouds().set(i, null);
                }
            }
        }
        // update Entrance after cloud choice
        ArrayList<Creature> creatureInEntranceAfterClouds = ackMessageMapped.getStudents();
        modelView.getSchoolBoardPlayers().get(ackMessageMapped.getRecipient()).getEntrancePlayer().setStudentsInTheEntrancePlayer(creatureInEntranceAfterClouds);


    }

    /**
     *This method is used to update the modelView after using a character card.
     * @param ackCharactersMessage is the ack received after a character card effect is resolved.
     */
    public void updateCharacterCard(AckCharactersMessage ackCharactersMessage){
        String characterUsed = ackCharactersMessage.getCharacter();
        //cli.characterConfirm(characterUsed);

        if(characterUsed.equals("monk")){
            modelView.getIslandGame().get(ackCharactersMessage.getIsland_ID()).addStudent(ackCharactersMessage.getStudent());
        }else if(characterUsed.equals("bard")){
            modelView.getSchoolBoardPlayers().get(ackCharactersMessage.getRecipient()).getEntrancePlayer().setStudentsInTheEntrancePlayer(ackCharactersMessage.getEntranceOfPlayer());
            modelView.getSchoolBoardPlayers().get(ackCharactersMessage.getRecipient()).getDiningRoomPlayer().setOccupiedSeatsPlayer(ackCharactersMessage.getPlayerDiningRoom());
            for(Integer player : ackCharactersMessage.getAllPlayersProfessors().keySet()) {
                for (Creature c : Creature.values()) {
                    if (ackCharactersMessage.getAllPlayersProfessors().get(player).contains(c)) {
                        modelView.getSchoolBoardPlayers().get(player).getProfessorTablePlayer().getOccupiedSeatsPlayer().replace(c, true);
                    } else {
                        modelView.getSchoolBoardPlayers().get(player).getProfessorTablePlayer().getOccupiedSeatsPlayer().replace(c, false);

                    }
                }
            }
        }else if(characterUsed.equals("jester")){
            modelView.getCharactersDataView().setJesterStudents(ackCharactersMessage.getStudentsOnCard());
            modelView.getSchoolBoardPlayers().get(ackCharactersMessage.getRecipient()).getEntrancePlayer().setStudentsInTheEntrancePlayer(ackCharactersMessage.getEntranceOfPlayer());
        }else if(characterUsed.equals("ambassador_influence")){
            matchEnd = ackCharactersMessage.isEndOfMatch();
            updateModelViewActionTwo(ackCharactersMessage);

        }else if (characterUsed.equals("ambassador_union")){
            matchEnd = ackCharactersMessage.isEndOfMatch();
            //updateModelViewActionTwo(ackCharactersMessage);


        }else if (characterUsed.equals("herbalist")){
            modelView.getIslandGame().get(ackCharactersMessage.getIsland_ID()).addNoEntryTile();
            modelView.getCharactersDataView().setHerbalistNumberOfNoEntryTile(ackCharactersMessage.getNumberOfElementsOnTheCard());

        }else if (characterUsed.equals("princess")){
            modelView.getCharactersDataView().setPrincessStudents(ackCharactersMessage.getStudentsOnCard());
            modelView.getSchoolBoardPlayers().get(ackCharactersMessage.getRecipient()).getDiningRoomPlayer().setOccupiedSeatsPlayer(ackCharactersMessage.getPlayerDiningRoom());
            for(Integer player : ackCharactersMessage.getAllPlayersProfessors().keySet()) {
                for (Creature c : Creature.values()) {
                    if (ackCharactersMessage.getAllPlayersProfessors().get(player).contains(c)) {
                        modelView.getSchoolBoardPlayers().get(player).getProfessorTablePlayer().getOccupiedSeatsPlayer().replace(c, true);
                    }else{
                        modelView.getSchoolBoardPlayers().get(player).getProfessorTablePlayer().getOccupiedSeatsPlayer().replace(c, false);
                    }
                }
            }
        }else if (characterUsed.equals("trafficker")){
            // set the professorTables
            for(int player_ID : ackCharactersMessage.getAllPlayersProfessors().keySet()) {
                for (Creature c : Creature.values()) {
                    if (ackCharactersMessage.getAllPlayersProfessors().get(player_ID).contains(c)) {
                        modelView.getSchoolBoardPlayers().get(player_ID).getProfessorTablePlayer().getOccupiedSeatsPlayer().replace(c, true);
                    }else{
                        modelView.getSchoolBoardPlayers().get(player_ID).getProfessorTablePlayer().getOccupiedSeatsPlayer().replace(c, false);
                    }
                }
            }

            // set the diningRooms
            HashMap<Integer, HashMap<Creature, Integer>> allDiningRooms = ackCharactersMessage.getAllPlayersDiningRoom();
            DiningRoomView diningRoom;
            for(int player_ID : allDiningRooms.keySet()){
                diningRoom = modelView.getSchoolBoardPlayers().get(player_ID).getDiningRoomPlayer();
                diningRoom.setOccupiedSeatsPlayer(allDiningRooms.get(player_ID));
            }
        }

        // update coins in the general reserve and the player's coins
        int newCoinPlayer = modelView.getCoinPlayer().get(ackCharactersMessage.getRecipient()) - (ackCharactersMessage.getCoinReserve() - modelView.getCoinGame());
        modelView.getCoinPlayer().replace(ackCharactersMessage.getRecipient(), newCoinPlayer);
        modelView.setCoinGame(ackCharactersMessage.getCoinReserve());

        // update character's price
        int characterIndex = modelView.getCharacterCardsInTheGame().indexOf(characterUsed);
        modelView.getCharactersPriceIncreased().set(characterIndex, true);
    }

    /**
     * This method is used to resolve the end of the match, and it calls the matchEnd cli method which notifies
     * the players that the match is over and the winner.
     * @param receivedMessageInJson is the message received in json.
     */
    public void matchIsEnded(String receivedMessageInJson){
        EndOfMatchMessage endOfMatchMessage = gsonObj.fromJson(receivedMessageInJson, EndOfMatchMessage.class);
        cli.matchEnd(endOfMatchMessage.getWinnerNickname(), endOfMatchMessage.getReason(), endOfMatchMessage.getWinner(), playerID);

        matchEnd = true;
    }

    public boolean isMessengerActive() {
        return messengerActive;
    }

    public int getJesterNumber() {
        return jesterNumber;
    }

    public void setJesterNumber(int jesterNumber) {
        this.jesterNumber = jesterNumber;
    }

    public void setLastCallFrom(String lastCallFrom) {
        this.lastCallFrom = lastCallFrom;
    }

    public boolean isMatchStarted() {
        return matchStarted;
    }

    public boolean isCharacterUsed() {
        return characterUsed;
    }

    public void setCharacterUsed(boolean characterUsed) {
        this.characterUsed = characterUsed;
    }

    public int getBardNumber() {
        return bardNumber;
    }

    public void setBardNumber(int bardNumber) {
        this.bardNumber = bardNumber;
    }

    /**
     * This method closes the client socket
     */
    public void closingNH() {
        try {
            this.inputBufferClient.close();
            this.outputPrintClient.close();
            this.clientSocket.close();
        }catch (IOException e) {
            e.printStackTrace();
        }
    }
}

