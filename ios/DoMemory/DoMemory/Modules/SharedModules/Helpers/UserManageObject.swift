//
//  UserManageObject.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 29/09/20.
//

import CoreData
class UserManageObject: NSObject {

    lazy var persistentContainer: NSPersistentContainer = {
        let container = NSPersistentContainer(name: "DoMemory")
        container.loadPersistentStores(completionHandler: { (storeDescription, error) in
            if let error = error as NSError? {
                fatalError("Unresolved error \(error), \(error.userInfo)")
            }
        })
        return container
    }()
    
    func getUserSettings() -> UserSettings? {

        let fetchRequest : NSFetchRequest<UserSettings> = UserSettings.fetchRequest()
        do {
            let result = try persistentContainer.viewContext.fetch(fetchRequest)
            return result.first
        } catch {
            return nil
        }
    }
    
    func createUserSettings(withDifficulty difficulty: Difficulty) {
        let userSettings = UserSettings(context: persistentContainer.viewContext)
        userSettings.dificulty  = difficulty.rawValue
        
        try? persistentContainer.viewContext.save()
    }
    
    func updateDifficulty(withDifficulty difficulty: Difficulty) {
        let fetchRequest : NSFetchRequest<UserSettings> = UserSettings.fetchRequest()
        do {
            let result = try persistentContainer.viewContext.fetch(fetchRequest)
            
            if let userSettings = result.first {
                
                userSettings.setValue(difficulty.rawValue, forKey: "dificulty")
                try? persistentContainer.viewContext.save()
            }
        } catch {
            return
        }
    }
    
    func clearAll() {
        let fetchRequest : NSFetchRequest<UserSettings> = UserSettings.fetchRequest()
        do {
            let result = try persistentContainer.viewContext.fetch(fetchRequest)
            for object in result {
                persistentContainer.viewContext.delete(object)
            }
        } catch {
            return
        }
    }
}

