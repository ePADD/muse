This package contains classes for Named entity recognition and implements algorithm in "Vihari et.al."
This is not an NER sequence labeller, but a classifier. The features for classification are simple surface features.

Tokeniser -- mention detector
Classes in this package identifies mentions and candidate entities in the text.
This package contains only single implementation that recognises mentions with Consecutive Initial Capital (CIC) pattern as discussed in the paper. This is pattern is very efficient and works great for semi formal texts like emails.

=====
Training
SVMModelTrainer -- trains an SVM classifier model
Input: 
1. Free text, in the form of documents Set<String>, though the document structure is not used in training.
2. Gazettes, database lists that contains both names and type of each name -- for example list of titles and types of DBpedia. Domain specific lists like addressbook can also be supplied.
3. List of types to train for
4. List of allowed types for each entity type that is being trained for. This should be coherent with the database types of the gazettes supplied in arguement 2.
5. FeatureGenerator object
6. Tokeniser
7. TrainParams
8. FeatureDictionary

=====
Recogniser
Input: 
1. Text
2. RecogniserModel
3. FeatureDictionary
4. Types to recognise