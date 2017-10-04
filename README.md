# reproducer-createIndexes

Minimal working example to send excessive amount of 'createIndexes' commands to MongoDB.

### Execution

1. Start MongoDB on localhost at port 27017 configured to log all commands. Tested with MongoDB version 3.2 and 3.4 
   and the storage engine 'wiredTiger'.
   ```sh
   export mongoVersion=3.4 # or 3.2

   docker run --name mongo -d -p 27017:27017 mongo:$mongoVersion --storageEngine wiredTiger --profile 2 --slowms 0
   ```

2. Execute `Main.main()`.

3. See hundreds of `createIndexes` commands in the MongoDB log made on the journal collection `my_journal`.
   ```sh
   docker logs mongo | grep 'createIndexes: "my_journal"' | wc -l
   ```
