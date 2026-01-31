# Konifer

Konifer is a high-performance, non-blocking REST API for managing images.
Built with Kotlin on the Ktor framework, it provides a flexible, path-driven design that allows clients to 
define their own hierarchical structure for image storage and retrieval.

## Documentation
To learn more about Konifer please visit the [documentation](https://dmaiken.github.io/konifer-docs/).

## Features

### Composable URL Structure
You define the path you want, limiting the need to store an identifier for an image. Fetching user 123's profile picture
no longer requires you to store "imageId = 2c3ee9c4-58b4-4d0c-8694-ab91125b5d3a" in your User service's DB. Instead,
define your path semantically:
```
GET /assets/user/123/profile
```
Konifer doesn't try to enforce any particular structure for assets, but it does provide a
simple way to define your own.

The way that Konifer stores and references the asset tree means there is no performance penalty for any path structure you
choose. If you wish to retain an "image ID", you can do so:
``` 
GET /assets/{imageId}
```

#### Multiple Images Within the Same Path
Konifer's path structure supports multiple images within the same path. When images are uploaded to a path, they are assigned an `entryId`,
an always-increasing value that uniquely identifies the image relative to the path.

For example, lets create an album for a ski trip (multi-part upload omitted) with 2 pictures:
``` 
POST /assets/users/{userId}/ski-trip
```
Response:
```json
{
	"class": "image",
	"entryId": 0,
	"labels": {},
	"tags": [],
	"source": "upload",
	"variants": [
		{
			"isOriginalVariant": true,
			"storeBucket": "assets",
			"storeKey": "513b9a74-0d78-40a3-9582-12bcb226fe1d.webp",
			"attributes": {
				"height": 1752,
				"width": 2560,
				"mimeType": "image/webp"
			},
			"lqip": {}
		}
	],
	"createdAt": "2025-12-26T23:02:43.042568",
	"modifiedAt": "2025-12-26T23:02:43.95746582"
}
```
```
POST /assets/users/{userId}/ski-trip
```
Response:
```json
{
	"class": "image",
	"entryId": 1,
	"labels": {},
	"tags": [],
	"source": "upload",
	"variants": [
		{
			"isOriginalVariant": true,
			"storeBucket": "assets",
			"storeKey": "2a09d243-82f8-42e2-ab64-16ec2adec793.webp",
			"attributes": {
				"height": 1600,
				"width": 2410,
				"mimeType": "image/webp"
			},
			"lqip": {}
		}
	],
	"createdAt": "2025-12-26T23:03:48.934689",
	"modifiedAt": "2025-12-26T23:03:48.934689"
}
```
You can access each image in the album by supplying the `entryId`:
``` 
GET /assets/users/{userId}/ski-trip/-/entry/1
```

### Variants (Transformations)

Konifer provides many ways to transform your images:
- Transformation of the original image content at upload-time
- Generate variants eagerly at upload-time for well-known transformations e.g. thumbnails
- On-demand variant generation for on-the-fly variant generation 

### Multiple Return Formats
You can specify your image data or metadata in any format you need:

#### Metadata
Fetch the metadata of your image, including generated variants
```
GET /assets/user/123/profile/-/metadata
```
```json
{
  "class": "IMAGE",
  "alt": "The alt text for an image",
  "entryId": 1049,
  "labels": {
    "label-key": "label-value",
    "phone": "Android"
  },
  "tags": [
    "cold",
    "verified"
  ],
  "source": "URL",
  // or UPLOAD if using multipart upload
  "sourceUrl": "https://yoururl.com/image.jpeg",
  "variants": [
    {
      "bucket": "assets",
      "storeKey": "d905170f-defd-47e4-b606-d01993ba7b42",
      "imageAttributes": {
        "height": 100,
        "width": 200,
        "mimeType": "image/jpeg"
      },
      "lqip": {
        // Empty if LQIPs are disabled
        "blurhash": "BASE64",
        "thumbhash": "BASE64"
      }
    }
  ],
  "createdAt": "2025-11-12T01:20:55"
}
```
#### Redirect
Request a 307 Redirect of your image's object repository's (e.g. S3) URL. Presigned URLs can be enabled for object repositories
that support it.
```
GET /assets/user/123/profile/-/redirect
GET /assets/user/123/profile/-/entry/1/redirect
```
```hocon 
s3 {
  presign {
    enabled = true
    ttl = 1h
  }
}
```

#### Content
Return the content bytes directly from Konifer. Your `alt` and any LQIP implementations are included as response headers.
```
GET /assets/user/123/profile/-/content
GET /assets/user/123/profile/-/entry/1/content
```

#### Download
Return the content bytes just like `content`, but include a `Content-Disposition` header so browsers trigger a "Save As" dialog.
```
GET /assets/user/123/profile/-/download
GET /assets/user/123/profile/-/entry/1/download
```

#### Link
Return a JSON containing the object repository link as well as other parameters necessary to properly render the image.
``` 
GET /assets/user/123/profile/-/link
GET /assets/user/123/profile/-/entry/1/link
```
```json
{
  "url": "https://assets.s3.us-east-2.amazonaws.com/d905170f-defd-47e4-b606-d01993ba7b42", // Or presigned if enabled
  "lqip": { // Empty if LQIPs are disabled
    "blurhash": "BASE64",
    "thumbhash": "BASE64"
  },
  "alt": "Your alt"
}
```

### Ordering
You can apply an ordering to the image(s) you're fetching:
- `created` (default) - order by last-created
- `modified` - order by last-modified

To fetch the latest image in the path:
``` 
GET /assets/user/123/profile/-/created
```
These allow you to use the `entryId` as a Version identifier.

### Limit
If requesting the `metadata` return format, you can also specify how many images you want returned.
``` 
GET /assets/user/123/profile/-/created?limit=10
```
To fetch all images within the path:
```
GET /assets/user/123/profile/-/created?limit=-1
```

### Named Transformations
For common transformations, simply configure a Variant Profile. Instead of specifying all parameters like this:
```
GET /assets/user/123/profile?w=300&blur=10&r=auto
```
With this configuration:
```hocon
variant-profiles = [
  {
    name = thumbnail
    w = 300
    blur = 10
    r = auto
  }
]
```
Supply the profile name:
``` 
GET /assets/user/123/profile?profile=thumbnail
```

## Building & Running

To build or run the project, use one of the following tasks:

| Task                          | Description                                                          |
|-------------------------------|----------------------------------------------------------------------|
| `./gradlew test`              | Run the tests                                                        |
| `./gradlew build`             | Build everything                                                     |
| `buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `buildImage`                  | Build the docker asset to use with the fat JAR                       |
| `publishImageToLocalRegistry` | Publish the docker asset locally                                     |
| `run`                         | Run the server                                                       |
| `runDocker`                   | Run using the local docker asset                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

## Running Locally
Before you run this locally, you must install libvips. You may have it already, however, installing it from source
ensures you're developing against the same version that Konifer will use within Docker. 

To install:
```shell
chmod +x /scripts/install-vips.sh && ./scripts/install-vips.sh --with-deps                                                                                                                                                                                                                                                                                                                      luci
```

## Docker

To build the docker image for this (highly recommended since it will contain all libraries needed by VIPS):
```shell
docker build . -t konifer:latest
```
Then, to run, mount a file to `/app/config/konifer.conf`:
```shell
docker run -v path/to/your/conf/file:/app/config/konifer.conf -p 8080:8080 konifer
```
Example:
```shell
docker run -v ~/konifer-test/config.conf:/app/config/konifer.conf -p 8080:8080 konifer
```

### Formatting

This project uses Ktlint to enforce code styling and Detekt for static analysis. To run both:

```shell
 ./gradlew ktlintFormat detekt
```

### JOOQ
This project used [JOOQ](https://www.jooq.org/) as it's interface to the database. JOOQ generates the code based on the database schema.
This is done within the `codegen` module. Running the code generator will:
1. Spin up a Postgres testcontainer
2. Run r2dbc-migrate against the database to apply the schema
3. Run the code generator against the constructed schema
4. Dump generated code into the `jooq-generated` module

To run this (which must be done if you make a schema change):

```shell
./gradlew generateJooq
```
**Note**: Linting is disabled for the `jooq-generated` module.

We do not want JOOQ to generate code for tables that the application will not interact with (e.g. db-scheduler tables).
Code generation for these tables can be skipped within the generator configuration file `CodeGen.kt` like so:

```kotlin
database = Database().apply {
    name = "org.jooq.meta.postgres.PostgresDatabase"
    inputSchema = "public"
    excludes = "migrations|scheduled_tasks|path_entry_counter" // regex for excluded tables
}
```

### License Report
To generate a license report, run:
```shell
./gradlew generateLicenseReport
```
