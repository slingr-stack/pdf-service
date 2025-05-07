---
title: PDF Service
keywords: 
last_updated: September 25, 2023
tags: []
summary: "Detailed description of PDF Service."
---

# Overview

The PDF service allows creating a PDF given an HTML using `wkhtmltopdf`.
Some of the features supported by this service are:

- Generate a PDF document from HTML
- Use Freemarker template to generate an HTML with a data object.
- Merge PDF documents
- Split PDF document
- Fill forms fields in PDF
- Replace images into the PDF
- Add images in the PDF

In order to get more information about [Freemarker Template](http://freemarker.org/) and [wkhtmltopdf](https://wkhtmltopdf.org/) 

## Configuration

### Max thread pool

The thread pool limits the number of threads that can be active to process pdf simultaneously. By default, it is 3.

## Settings

You can set specific properties in document.

**name:** this is the file name. If it is null the default is `pdf-{timestamp}`.
 
**pageSize:** this is the document page size. It can be `letter`, `A4`, etc. Default value is `A4`. You can check `wkhtmltopdf` for more options.

**orientation:** set orientation to `landscape` or `portrait` (default `portrait`).

**marginBottom:** bottom margin in mm.

**marginLeft:** left margin in mm (default 10mm)

**marginRight:** right margin in mm (default 10mm)
 
**marginTop:** top margin in mm

**headerTemplate:** Freemarker template to generate an HTML header. `<!DOCTYPE html>` is required to avoid empty document.

**headerData** data used in header template.

**footerTemplate** Freemarker template to generate an HTML footer. `<!DOCTYPE html>` is required to avoid an empty document.

**footerData** data used in footer template.

**imageDpi:** when embedding images scale them down to this dpi. Default value is `600`. You can check `wkhtmltopdf` for more options.

**imageQuality:** when jpeg compressing images use this quality.  Default value is `94`. You can check `wkhtmltopdf` for more options.

**lowquality:** generates lower quality pdf/ps. Useful to shrink the result document space. Default value is `false`. You can check `wkhtmltopdf` for more options.

**footerFontName:** Set footer font name (default Arial). You can check `wkhtmltopdf` for more options.

**footerFontSize:** Set footer font size (default 12). You can check `wkhtmltopdf` for more options.

**headerFontName:** Set header font name (default Arial). You can check `wkhtmltopdf` for more options.

**headerFontSize:** Set header font size (default 12). You can check `wkhtmltopdf` for more options.

## Generated PDF

Generated PDF is returned as a file structure.

```json
{
  status: "ok",
  file: {
    fileId: "...",
    fileName: "...",
    contentType: "application/pdf"
  }
}
```

## Quick start

```js
var tpl  = "<html>";
    tpl += "<body>";
    tpl += "<h1>${title}</h1>";
    tpl += "<#list items as item>";
    tpl += "<tr><td>${item.name}</td></tr>";
    tpl += "</#list>";
    tpl += "</body>";   
    tpl += "</html>";
var data = {
    title: "Example PDF",
    items: [
        {
            name: "Item 1"
        },
        {
            name: "Item 2"
        }
    ]
};
var headerTemplate = "<!DOCTYPE html>"+
                     "  <html>"+
                     "    <head></head>"+
                     "    <body>"+
                     "      <h2>** Good News: ${title}</h2>"+
                     "      Page <span id='page'></span> of"+ 
                     "      <span id='topage'></span>   "+
                     "      <hr />"+ 
                     "      <script> "+
                     "        var vars={};"+
                     "        var x=window.location.search.substring(1).split('&');"+
                     "        for (var i in x) {"+
                     "          var z=x[i].split('=',2);"+
                     "          vars[z[0]] = unescape(z[1]);"+
                     "        }"+
                     "        document.getElementById('page').innerHTML = vars.page;"+ 
                     "        document.getElementById('topage').innerHTML = vars.topage;"+ 
                     "      </script> "+
                     "    </body>"+
                     "  </html>";

var headerData = { title: 'Page title!!' };
var footerTemplate = "<!DOCTYPE html><html><body><h3>Page here ## ${name}</h3></body></html>";
var footerData = { name: 'User Name' };
var settings = {
    name: "MyPdfFile",
    pageSize: "letter",
    headerTemplate: headerTemplate,
    headerData: headerData,
    footerTemplate: footerTemplate,
    footerData: footerData
};

svc.pdf.generatePdf({template: tpl, data: data, settings: settings, callbackData: { record: record }}, {
    pdfResponse: function(res, resData){
        var data = res.data;
        var document = resData.record;
        if(data && data.status == "ok"){
          document.field('name').val(data.file.fileName);
          const fileId = data.file.fileId;
          document.field('file').val({
            id: fileId, 
            name: data.file.fileName,
            contentType: data.file.contentType
          });
          sys.logs.debug(sys.files.share(fileId)+'?download=false');
          sys.data.save(document);

        }
      }
});
```

## Merge PDF documents

Given a PDF list, return a PDF file with merged pages that are specified in arguments.

**file:** this is the file id. Required.
 
**start:** used to specify started page to merge in case you want to split the document. Optional.

**end:** used to specify ended page to merge in case you want to split the document. Optional.


```js
svc.pdf.mergeDocuments({ documents: [
      { 'file': '5a537d6ad6671c33a238519a', 'start': 1, 'end': 1 },
      { 'file': '5a537d6dd6671c33a238519e', 'start': 32, 'end': 33 },
      { 'file': '5a537d71d6671c33a23851a2', 'start': 2, 'end': 2 }
    ], callbackData: { record: record }, callback: { 'pdfResponse': function(res, resData){
      var data = res.data;
      var document = resData.record;
        if(data && data.status == "ok"){
          document.field('name').val(data.file.fileName);
          document.field('mergedFile').val({
            id: data.file.fileId, 
            name: data.file.fileName,
            contentType: data.file.contentType
          });
          sys.data.save(document);
        }
    }
  }});
```

## Split PDF document

Given a PDF file and an interval return a list of PDF files divided.

**fileId:** this is the file id. Required.
 
**interval:** indicates every how many pages we will split the document. For example, if it is 1, then every page will become a new file.

**startPage:** indicates in which page index we will start splitting the document. For example, if it is 30, then we will split from page 31. Default: 0

**endPage:** indicates in which page index we will stop splitting the document. For example, if it is 59, then we will split until page 60. Default: document size.

**filePrefix:** indicates every how many pages we will split the document. For example, if it is 1, then every page will become a new file. Default: "split-doc-"

```js
svc.pdf.splitDocument({ fileId: '5ad8a06ca0be513068b65dee', interval: 2, callbackData: { record: record }, callbacks: {
    'pdfResponse': function(res, resData) {
        var data = res.data;
        var document = resData.record;
        if(data && data.status == "ok") {
            var files = [];
            for(var i in data.files) {
              var splitFile = data.files[i];
              files.push({
                  id:splitFile.fileId, 
                  name: splitFile.fileName,
                  contentType: splitFile.contentType
                });
            }
          document.field('files').val(files); // where file is multi value file type
          sys.data.save(document);
      }
    }
}});
```

## Replace header and footer

Given a PDF file, you can replace its header and footer using images or html templates.

### Settings using images

**header.imageId** file id of image to set as header

**header.height** the height of the output header

**header.width** the width of the output header

**footer.imageId** file id of image to set as footer

**footer.height** the height of the output footer

**footer.width** the width of the output footer

```javascript
    var fileId = record.field('pdf').id();
    var settings = {
      header: {
        imageId: record.field('headerImage').id(),
        height: 120
      },
      footer: {
        imageId: record.field('footerImage').id(),
        height: 75
      }
    };
    svc.pdf.replaceHeaderAndFooter({ fileId: fileId, settings: settings, callbackData: {record: record}, callbacks: {
      pdfResponse: function(res, resData){
        var data = res.data;
        var document = resData.record;
        if(data && data.status == "ok"){
          document.field('newPdf').val({
            id: data.file.fileId, 
            name: data.file.fileName,
            contentType: data.file.contentType
          });
          sys.data.save(document);
        }
      }
    }});
```

### Settings using html templates and data

**header.html** Freemarker template to generate an HTML header.

**header.data** Data used in header template.

**header.height** the height of the output header

**header.width** the width of the output header

**footer.html** Freemarker template to generate an HTML footer.

**footer.data** Data used in footer template.

**footer.height** the height of the output footer

**footer.width** the width of the output footer

```javascript
    var fileId = record.field('pdf').id();
    var settings = {
      header: {
        html: "<html><body><h3>${company} - ${year}</h3></body></html>",
        data: {company: "slingr.io", year: "2019"},
        height: 120
      },
      footer: {
        html: "<html><body><small>${application} - ${year}</small></body></html>",
        data: {application: "New Application", year: "2018"},
        height: 75
      }
    };
    svc.pdf.replaceHeaderAndFooter({fileId: fileId, settings: settings, callbacks: {record: record}, callbackData: {
      pdfResponse: function(res, resData){
        var data = res.data;
        var document = resData.record;
        if(data && data.status == "ok"){
          document.field('newPdf').val({
            id: data.file.fileId, 
            name: data.file.fileName,
            contentType: data.file.contentType
          });
          sys.data.save(document);
        }
      }
    }});
```

## Fill forms fields in PDF

Given a PDF is allowed fill form sending data fields values in settings.
As a result, a new PDF file is generated 
with filled information.

- **name:** this is the pdf file name. If it is null the default is `pdf-{timestamp}`.
- **data:** this is a description of fields to be filled. Can be the string to fill or object with the value and style 
as it is described below.
    - **value:** text to fill the field
    - **textColor:** string with the hexadecimal code of the text color.
    - **backgroundColor:** string with the hexadecimal code of the background color.
    - **textSize:** integer with font size.
    - **textAlignment:** string with justification of the text. Possible values `LEFT`, `CENTER` and `RIGHT`
    - **readOnly:** set the form field as read only. Default value is false 
    - **fontFileId:** this is a reference to font source file. It is a Slingr file type id that service use to fill the form field.
    
```javascript
var fileId = record.field('myPdf').val().id;
var settings = {
  name: 'my-custom-file-name',
  data: {
    dateAndMonth: action.field('text').val(),
    age: {
      value: action.field('age').val(),
      textColor: '#4669f2',     
      textSize: 9,
      textAlignment: 'CENTER',
      backgroundColor: '#45bf2c',
      readOnly: true,
      fontFileId: '5e2069fe35768008afb01cb9'
    }
  }
};
svc.pdf.fillForm({fileId: fileId, settings: settings, callbackData: {record: record}, callbacks: {
      pdfResponse: function(res, resData){
        var data = res.data;
        var document = resData.record;
        if(data && data.status == "ok"){
          document.field('filledFile').val({
            id: data.file.fileId, 
            name: data.file.fileName,
            contentType: data.file.contentType
          });
          sys.data.save(document);
        }
      }
}});
```

## Replace images into the PDF

Given a PDF is allowed to replace selected images sending the source images as setting parameter.
It is necessary to specify the index as well as file id of the image.
As a result, a new PDF file is generated with given images 
in specified position.

### Settings

**name:** this is the pdf file name. If it is null the default is `pdf-{timestamp}`.

It is necessary to send an array with images like:

**index** the source image index in the PDF document. Required.

**fileId** the target image id to be replaced in the PDF document. Required.

```javascript
var fileId = record.field('myPdf').val().id;
var settings = {
    name: 'my-custom-file-name',
    images: [
      { index: 0, fileId: record.field('image1').id() },
      { index: 1, fileId: record.field('image2').id() }
    ]
}; 
svc.pdf.replaceImages({fileId: fileId, settings: settings, callbackData: {record: record}, callbacks: {
    pdfResponse: function(res, resData) {
      var data = res.data;
      var document = resData.record;
      if(data && data.status == "ok") {
        document.field('filledFile').val({
          id: data.file.fileId, 
          name: data.file.fileName,
          contentType: data.file.contentType
        });
        sys.data.save(document);
      }
    }
}});
```

## Add images in the PDF

Given a PDF is allowed to append selected images sending as setting parameter. It is necessary 
to specify the page index as well as file id of the image. As a result, a new PDF file is generated with added images 
in specified position.

### Settings

**name:** this is the pdf file name. If it is null the default is `pdf-{timestamp}`.

It is necessary to send an array with images like:

**pageIndex** the page to add the image. Required.

**fileId** the image id to be added in selected page. Required.

**x** horizontal position for selected page. Value 0 is the bottom left. Default value 20.

**y** vertical position for selected page. Value 0 is the bottom left. Default value 20.

**width** width size for image. Default value 100.

**height** the height size for the image. Default 100.

**fullPage** if set to `true`, the params `x`, `y`, `width`, and `height` will be discarded and the
image will take the whole page.

```javascript
var fileId = record.field('myPdf').val().id;
var settings = {
    name: 'my-custom-file-name',
    images: [
      { pageIndex: 0, fileId: record.field('image1').id(), x: 100, y: 250, width: 70, height: 100 },
      { pageIndex: 1, fileId: record.field('image2').id(), x: 20, y: 20, width: 400, height: 300 }
    ]
}; 
svc.pdf.addImages({fileId: fileId, settings: settings, callbackData: {record: record}, callbacks: {
    pdfResponse: function(res, resData) {
      var data = res.data;
      var document = resData.record;
      if(data && data.status == "ok") {
        document.field('filledFile').val({
          id: data.file.fileId, 
          name: data.file.fileName,
          contentType: data.file.contentType
        });
        sys.data.save(document);
      }
    }
}});
```

## Convert PDF to images

Given a list of pdf ids, an object is returned containing for each id, a list of the pages of that file converted
to images.

**fileIds:** this is a list of the ids of the pdfs. Required.

**dpi:** represents the number of pixels per inch and therefore the resolution we want
the converted pdf images to have. This can't be greater than 600. Required.

**format:** the format of the image. If can be GIF, PNG, JPEG, BMP and WBMP. Default is JPEG.

```js
svc.pdf.convertPdfToImages({fileIds: ['8ko8a06ca0be213068b65dee', '89osa06ca0be513068b2fgcg'], dpi: 72, callbacksData: { record: record }, callbacks: {
    'pdfResponse': function(res, resData) {
        var data = res.data;
        var document = resData.record;
        if(data && data.status == "ok") {
            var files = [];
            // Here we iterate through the different pdfs ids
            for(var id in data.imagesIds) {
              // We can then iterate over the pages of the pdf that were converted
              data.imagesIds[id].forEach(function(imageId) {
                files.push(imageId);
              });
            }
          document.field('files').val(files); // where file is multi value file type
          sys.data.save(document);
      }
    }
}});
```
This is an example of the response
```json
{
  "status": "ok",
  "imagesIds": {
      "8ko8a06ca0be213068b65dee": [
         "632493fa17170858125a6e1a",
         "632493fc17170858125a6e1d"
      ],
      "89osa06ca0be513068b2fgcg": [
         "6324940517170858125a6e21",
         "6324940817170858125a6e24",
         "6324940b17170858125a6e27",
         "6324940d17170858125a6e2a"
      ]
   }
}
```

There is a sync version that doesn't need a callback:

```js
let data = svc.pdf.convertPdfToImages({fileIds: ['8ko8a06ca0be213068b65dee', '89osa06ca0be513068b2fgcg'], dpi: 72, format: 'PNG'});
if (data && data.status == "ok") {
    var files = [];
    // Here we iterate through the different pdfs ids
    for(var id in data.imagesIds) {
      // We can then iterate over the pages of the pdf that were converted
      data.imagesIds[id].forEach(function(imageId) {
        files.push(imageId);
      });
    }
  document.field('files').val(files); // where file is multi value file type
  sys.data.save(document);
}
```

## Convert PDF to text

This feature allows the conversion of a PDF file to text by sending the file ID as a setting parameter. The resulting text will be extracted from the provided PDF and returned as a response.

**fileId:** The ID of the PDF file to be converted. Required.

```js
var fileId = record.field('myPdf').val().id;

svc.pdf.convertPdfToText({fileId: fileId, callbackData: {record: record}, callbacks: {
    pdfResponse: function(res, resData) {
      var data = res.data;
      var document = resData.record;
      if(data && data.status == "ok") {
        document.field('extractedText').val(data.pdfText);
        sys.data.save(document);
      } else {
        logger.error("PDF Conversion failed: " + data.message);
      }
    }
  }});
```
This is an example of the response
```json
{
  "status": "ok",
  "message": "PDF conversion completed.",
  "pdfText": "This is the extracted text from the PDF."
}
```

## About SLINGR

SLINGR is a low-code rapid application development platform that accelerates development, with robust architecture for integrations and executing custom workflows and automation.

[More info about SLINGR](https://slingr.io)

## License

This service is licensed under the Apache License 2.0. See the `LICENSE` file for more details.
