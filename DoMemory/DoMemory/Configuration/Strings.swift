//
//  Strings.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import Foundation

enum Strings {
    static let easy = NSLocalizedString("Fácil", comment: "Easy difficulty level")
    static let easySubtitle = NSLocalizedString("Inicio relajado", comment: "Subtitle for easy difficulty")
    static let medium = NSLocalizedString("Normal", comment: "Medium difficulty level")
    static let mediumSubtitle = NSLocalizedString("Un reto justo", comment: "Subtitle for medium difficulty")
    static let hard = NSLocalizedString("Difícil", comment: "Hard difficulty level")
    static let hardSubtitle = NSLocalizedString("Afila tu memoria", comment: "Subtitle for hard difficulty")
    static let difficulty = NSLocalizedString("Dificultad", comment: "Difficulty section title")
    static let askDifficulty = NSLocalizedString("Selecciona la dificultad", comment: "Prompt to select difficulty")
    static let veryHard = NSLocalizedString("Muy difícil", comment: "Very hard difficulty level")
    static let veryHardSubtitle = NSLocalizedString("Solo para expertos", comment: "Subtitle for very hard difficulty")
    static let yourPoints = NSLocalizedString("Puntos", comment: "")
    static let time = NSLocalizedString("Tiempo", comment: "")
    static let pause = NSLocalizedString("Pausa", comment: "")
    static let quit = NSLocalizedString("¿Estás seguro que quieres salir?", comment: "")
    static let ok = NSLocalizedString("Ok", comment: "")
    static let cancel = NSLocalizedString("Cancelar", comment: "")
    static let accept = NSLocalizedString("Aceptar", comment: "")
    static let youLose = NSLocalizedString("Lo siento se te acabo el tiempo", comment: "")
    static let tryAgain = NSLocalizedString("Intentar de nuevo", comment: "")
    static let continueGame = NSLocalizedString("Continuar", comment: "")
    static let goToMenu = NSLocalizedString("Ir al menú", comment: "")
    static let youWin = NSLocalizedString("!Felicidades¡", comment: "")
    static let youWinDescription = NSLocalizedString("Lograste terminar a tiempo esté memorama", comment: "")

    // Menu tabs
    static let tabAll = NSLocalizedString("Todos", comment: "Tab showing all games")
    static let tabMine = NSLocalizedString("Mis memoramas", comment: "Tab showing user-created games")

    // Create memorama sheet
    static let createTitle = NSLocalizedString("Nuevo Memorama", comment: "Sheet title for creating a new memorama")
    static let createName = NSLocalizedString("Nombre", comment: "Label for memorama name field")
    static let createNamePlaceholder = NSLocalizedString("Ej: Frutas, Animales…", comment: "Placeholder for memorama name field")
    static let createEmojisCount = NSLocalizedString("Emojis", comment: "Label prefix for emoji count")
    static let createMinimum = NSLocalizedString("Mínimo 2", comment: "Hint that at least 2 emojis are required")
    static let createAddEmoji = NSLocalizedString("Agregar emoji", comment: "Button to add an emoji")
    static let createAdd = NSLocalizedString("Agregar", comment: "Confirm adding an emoji")
    static let createSave = NSLocalizedString("Guardar", comment: "Save button in create memorama sheet")

    // My memoramas empty state
    static let emptyMyGames = NSLocalizedString("Aún no tienes memoramas propios", comment: "Empty state message for user-created games")
    static let emptyMyGamesAction = NSLocalizedString("Crear uno", comment: "CTA button in empty state")

    // Delete action
    static let delete = NSLocalizedString("Eliminar", comment: "Delete action in context menu")
}
