package controller;

import model.Game;
import model.players.ComputerPlayer;
import model.players.HumanPlayer;
import model.players.HumanPlayerAdvanced;
import model.players.NaiveStrategy;
import model.players.Player;
import model.piles.Hand;
import model.piles.StockPile;
import view.LocalTuiView;


import java.util.ArrayList;
import java.util.List;

public class MainAdvanced {
    public static void main(String[] args) {
        int players = 2;
        int humans = 0;

        try {
            if (args.length > 0) {
                players = Integer.parseInt(args[0]);
            }
            if (args.length > 1) {
                humans = Integer.parseInt(args[1]);
            }
        } catch (NumberFormatException e) {
            System.out.println("Usage: java Local.MainAdvanced [players] [humans]");
            return;
        }

        if (players < 2 || players > 6) {
            System.out.println("Players must be between 2 and 6.");
            return;
        }
        if (humans < 0 || humans > players) {
            System.out.println("Humans must be between 0 and players.");
            return;
        }

        List<Player> allPlayers = new ArrayList<>();
        for (int i = 1; i <= humans; i++) {
            String name = humans == 1 ? "You" : "P" + i;
            allPlayers.add(new HumanPlayerAdvanced(name, new Hand(), new StockPile()));
        }
        int botCount = players - humans;
        for (int i = 1; i <= botCount; i++) {
            allPlayers.add(new ComputerPlayer("CPU " + i, new Hand(), new StockPile(), new NaiveStrategy()));
        }

        Game game = new Game(allPlayers);
        long lastRenderedProgress = -1L;
        int noProgressTurns = 0;
        int maxNoProgressTurns = Math.max(100, game.getPlayers().size() * 20);
        Game.RoundResult forcedResult = null;

        while (!game.isMatchOver()) {
            while (!game.gameOver()) {
                long progressVersion = game.getProgressVersion();
                boolean shouldRender = progressVersion != lastRenderedProgress
                        || game.getCurrentPlayer() instanceof HumanPlayer;
                if (shouldRender) {
                    LocalTuiView.printBoard(System.out, game);
                    lastRenderedProgress = progressVersion;
                }
                long progressBefore = game.getProgressVersion();
                game.playOneTurn();
                long progressAfter = game.getProgressVersion();
                if (progressAfter == progressBefore) {
                    noProgressTurns++;
                    if (noProgressTurns >= maxNoProgressTurns) {
                        LocalTuiView.printBoard(System.out, game);
                        System.out.println("\nStalemate detected for " + maxNoProgressTurns
                                + " turns. Ending round.");
                        forcedResult = game.resolveStalemate();
                        break;
                    }
                } else {
                    noProgressTurns = 0;
                }
            }

            Game.RoundResult result = forcedResult != null ? forcedResult : game.finishRound();
            System.out.println("\nRound over! Winner: " + result.getWinner().getName()
                    + " (+" + result.getPointsAwarded() + ", total " + result.getTotalScore() + ")");

            if (!game.isMatchOver()) {
                game.startNewRound(result.getWinner());
                lastRenderedProgress = -1L;
                noProgressTurns = 0;
                forcedResult = null;
            }
        }

        System.out.println("\nMatch over! Winner: " + game.getMatchWinner().getName());
    }
}
