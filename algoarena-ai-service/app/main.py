from fastapi import FastAPI
app=FastAPI(title="algoarena-ai-service")
@app.get("/api/health")
def health():
    return {"status":"ok","service":"algoarena-ai-service"}