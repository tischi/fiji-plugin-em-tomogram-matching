package explore;

import org.json.JSONException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


import java.io.FileReader;
import java.io.IOException;

public class ReadJson
{
	public static void main( String[] args ) throws IOException, ParseException
	{

		JSONParser parser = new JSONParser();

		JSONObject jsonObject = ( JSONObject ) parser.parse(
				new FileReader("/Users/tischer/Documents/giulia-mizzon-CLEM--data/" +
						"json-test/A120g4-a1.tif (RGB).meta.json"));

		for ( Object key : jsonObject.keySet() )
		{
			System.out.println( key.toString() + ": " );
			System.out.println( jsonObject.get( key ));
			System.out.println( "\n");
		}

//		System.out.println( jsonObject.toString() );
//		for (Object o : a)
//		{
////			JSONObject person = (JSONObject) o;
//			System.out.println( o.toString() );

//			String name = (String) person.get("TargetMap");
//			System.out.println(name);

//			String city = (String) person.get("city");
//			System.out.println(city);
//
//			String job = (String) person.get("job");
//			System.out.println(job);
//
//			JSONArray cars = (JSONArray) person.get("cars");
//
//			for (Object c : cars)
//			{
//				System.out.println(c+"");
//			}
//		}
	}
}
