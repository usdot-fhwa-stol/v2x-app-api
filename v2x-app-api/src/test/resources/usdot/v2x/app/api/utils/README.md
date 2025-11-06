To generate these files the following process was used:

1. Follow the JSON schema to create a TIM with the ODEs `/tim` post endpoint.
2. Look at the `topic.OdeTimJsonTMCFiltered` and grab the hex from "metadata" -> "asn1".
3. Create a file in this folder with the hex content.
4. Startup the partner API and hit the `/api/v2/decode/hex` with the hex asn1 of the desired TIM.
5. Copy the returned JSON content into a file in this folder to use for unit tests.