{-# LANGUAGE BangPatterns #-}

import System.IO
import Data.Set (Set)
import Data.Map (Map)
import Data.ByteString.UTF8 
import qualified Data.Set as Set
import qualified Data.Map as Map

-- Program to extract lat-longs for city names from freebase data (assumed to be stored in /x/data/location)
-- 3 files read: citytown.tsv (list of city names), location.tsv (city name to geocode entity), geocode.tsv (geocode entity to lat long)

main = do 
        -- read city names
        inh <- openFile "/x/data/location/citytown.tsv" ReadMode
        cities  <- getCities inh []
        print $ "#cities " ++ show  (Set.size cities) -- prints about 322000
        hClose inh

        -- read geocodes
        locationH <- openFile "/x/data/location/location.tsv" ReadMode -- this file has about 1.4M lines
        cityNameToGeocode <- getGeocodes locationH cities [] 
        print $ "city name -> geocode map size " ++ show (Map.size cityNameToGeocode)
        hClose locationH

        -- read lat-longs
        latLongH <- openFile "/x/data/location/geocode.tsv" ReadMode -- this file has about 840K lines
        -- locationH <- openFile "/tmp/2" ReadMode
        geocodeToLatLong <- getLatLong latLongH (Set.fromList (Map.elems cityNameToGeocode)) [] 
        print $ "geocode -> lat long map size " ++ show (Map.size geocodeToLatLong)
        hClose latLongH
        
        -- results now stored in cities, cityNameToGeocode, geocodeToLatLong

        -- outh <- openFile "/tmp/citylatlong" WriteMode
        writeLoop (Set.toList cities) cityNameToGeocode geocodeToLatLong 
        print "x"

writeLoop cities cityNameToGeocode geocodeToLatLong =
      do
         if cities == [] then return ("")
 	 else do
              let 
                city = head cities
                geocode = case (Map.lookup city cityNameToGeocode) of
                          Nothing -> "__DUMMY__"
                          Just g -> g
                ((lat, long), found) = case (Map.lookup geocode geocodeToLatLong) of 
                                       Nothing -> (("", ""), False)
                                       Just x -> (x, True)
              putStrLn (if found then (show (Data.ByteString.UTF8.fromString city) ++ "\t" ++ show lat ++ "\t" ++ show long) else "")
              writeLoop (tail cities) cityNameToGeocode geocodeToLatLong
        
getCities :: Handle -> [[Char]] -> IO (Set [Char])
getCities inh cityNamesList = 
   do ineof <- hIsEOF inh
      if ineof then return (Set.fromList cityNamesList)
      else do inpStr <- hGetLine inh
              getCities inh ((head (tokenize inpStr '\t')) : cityNamesList)

parseCityCodeLine :: [Char] -> ([Char], [Char])
parseCityCodeLine line = let tokens = (tokenize line '\t') in (tokens !! 0, tokens !! 3) -- fields #0 and #3 have the name and code resply.

parseGeocodeLine :: [Char] -> ([Char], [Char], [Char])
parseGeocodeLine line = let tokens = (tokenize line '\t') in (tokens !! 1, tokens !! 2, tokens !! 3) -- fields #0=geocode, 1=long, 2=lat
        
getGeocodes :: Handle -> Set [Char] -> [([Char], [Char])] -> IO (Map [Char] [Char])
getGeocodes locationH cities cityCodePairs = 
   do ineof <- hIsEOF locationH
      if ineof then return (Map.fromList cityCodePairs)
      else do 
           inpStr <- hGetLine locationH
           let (place, geocode) = parseCityCodeLine inpStr
               cityCodePairs' = if (Set.member place cities) then ((place, geocode): cityCodePairs) else cityCodePairs 
           getGeocodes locationH cities cityCodePairs' 

-- parses geocode file in the form of "name long lat" and return map of geocode -> (lat, long) pairs
getLatLong :: Handle -> Set [Char] -> [([Char], ([Char], [Char]))] -> IO (Map [Char] ([Char], [Char]))
getLatLong geocodeH geocodesToGet nameToLatLongPairs =
   do ineof <- hIsEOF geocodeH
      if ineof then return (Map.fromList nameToLatLongPairs)
      else do 
           inpStr <- hGetLine geocodeH
           -- print (Set.size geocodesToGet)
           -- print (show nameToLatLongPairs)
           let (geocode, long, lat) = parseGeocodeLine inpStr
               nameToLatLongPairs' = if (Set.member geocode geocodesToGet) then ((geocode, (lat, long)): nameToLatLongPairs) else nameToLatLongPairs 
           getLatLong geocodeH geocodesToGet nameToLatLongPairs' 
      
-- find first index (0-based) of c in s; -1 if c is not in s
-- indexOf :: forall a a1. (Num a, Eq a1) => [a1] -> a1 -> Maybe a
indexOf s c = indexOf' s c 0 where 
  indexOf' [] _ _ = Nothing
  indexOf' (x:y) c i = if x==c then Just i else indexOf' y c (i+1)

-- should tokenize the given string using the given char and return a list of tokens
-- tokenize :: forall a. Eq a => [a] -> a -> [[a]]
tokenize s c = case (indexOf s c) of 
                Nothing -> [s]
                Just idx -> ((Prelude.take idx s) : (tokenize (Prelude.drop (idx+1) s) c))

