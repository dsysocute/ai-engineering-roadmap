from fastapi import FastAPI
app=FastAPI(title="algoarena")
@app.get("/api/health")
def health():
    return{"status":"ok",
           "service":"algoarena-ai-service"}